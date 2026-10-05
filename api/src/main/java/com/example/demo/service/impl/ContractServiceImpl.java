package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.*;
import com.example.demo.enums.ContractBizTypeEnum;
import com.example.demo.enums.ContractStatusEnum;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.mapper.*;
import com.example.demo.service.ContractService;
import com.example.demo.service.MessageService;
import com.example.demo.service.strategy.FileStorageStrategy;
import com.example.demo.utils.ContractHashUtils;
import com.wwfinance.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.example.demo.utils.PageUtils;

/**
 * 电子合同服务实现
 */
@Slf4j
@Service
public class ContractServiceImpl extends ServiceImpl<ContractMapper, Contract> implements ContractService {

    /** 手写签名图大小上限（解码后字节数） */
    private static final int SIGN_IMG_MAX_BYTES = 2 * 1024 * 1024;
    /** 签署截止时间：合同创建后 7 天内 */
    private static final int SIGN_VALID_DAYS = 7;
    private static final DateTimeFormatter SIGN_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private ContractTemplateMapper contractTemplateMapper;
    @Autowired
    private ContractSignRecordMapper contractSignRecordMapper;
    @Autowired
    private LendMapper lendMapper;
    @Autowired
    private LendItemMapper lendItemMapper;
    @Autowired
    private BorrowerMapper borrowerMapper;
    @Autowired
    private InvestorMapper investorMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FileStorageStrategy fileStorageStrategy;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MessageService messageService;

    // ==================== 创建 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Contract getOrCreate(ContractBizTypeEnum bizType, Long bizId, ContractTypeEnum type, Long partyId,
                                BigDecimal amount) {
        Lend lend = lendMapper.selectById(bizId);
        if (lend == null) {
            throw new ResourceNotFoundException(500, "标的不存在，无法生成合同");
        }
        BigDecimal contractAmount = amount != null ? amount : lend.getAmount();

        // 金额是合同身份的一部分：同一投资人换个金额就等于换了一份合同，需要重新签署。
        // 所以按 (业务, 类型, 主体, 金额) 复用已有合同，金额不同则新插一行（不做软删除，
        // 否则 is_deleted=1 的行会互相撞 uk_biz）。
        Contract exist = findOne(bizType, bizId, type, partyId, contractAmount);
        if (exist != null) {
            return exist;
        }

        ContractTemplate tpl = loadEnabledTemplate(type);
        if (tpl == null) {
            throw new ResourceNotFoundException(500,
                    "《" + type.getDesc() + "》的合同模板未配置或已停用，请先在管理端配置模板");
        }

        String contractNo = genContractNo();
        Map<String, String> vars = buildVariables(type, lend, partyId, contractNo, contractAmount);
        String content = render(tpl.getContentTpl(), vars);

        Contract c = new Contract();
        c.setContractNo(contractNo);
        c.setContractType(type.getCode());
        c.setBizType(bizType.getCode());
        c.setBizId(bizId);
        c.setTemplateId(tpl.getId());
        c.setTemplateVersion(tpl.getVersion());
        c.setAmount(contractAmount);
        c.setLendYearRate(lend.getLendYearRate());
        c.setPeriod(lend.getPeriod());
        c.setStartDate(lend.getLendStartDate());
        c.setEndDate(lend.getLendEndDate());
        c.setContentSnapshot(content);
        c.setContentHash(sha256(content));
        c.setStatus(ContractStatusEnum.PENDING.getCode());
        c.setSignDeadline(LocalDateTime.now().plusDays(SIGN_VALID_DAYS));
        fillParties(c, type, lend, partyId);
        this.save(c);
        log.info("合同已生成: no={}, type={}, bizType={}, bizId={}, partyA={}, amount={}",
                contractNo, type.getCode(), bizType.getCode(), bizId, partyId, contractAmount);
        return c;
    }

    /** 甲方/乙方按合同类型归属：出借协议甲方=投资人、乙方=借款人；其余甲方=借款人 */
    private void fillParties(Contract c, ContractTypeEnum type, Lend lend, Long partyId) {
        Long borrowerId = lend.getUserId();
        if (type == ContractTypeEnum.INVEST) {
            c.setPartyAId(partyId);
            c.setPartyAName(resolveName(partyId, false));
            c.setPartyBId(borrowerId);
            c.setPartyBName(resolveName(borrowerId, true));
        } else {
            c.setPartyAId(borrowerId);
            c.setPartyAName(resolveName(borrowerId, true));
        }
    }

    /** 姓名取实名认证档案，缺失时退回用户昵称/手机号，保证合同不会出现空白主体 */
    private String resolveName(Long userId, boolean borrowerSide) {
        if (userId == null) {
            return "未知";
        }
        if (borrowerSide) {
            Borrower b = borrowerMapper.selectOne(new LambdaQueryWrapper<Borrower>()
                    .eq(Borrower::getUserId, userId).last("LIMIT 1"));
            if (b != null && StringUtils.hasText(b.getName())) {
                return b.getName();
            }
        } else {
            Investor i = investorMapper.selectOne(new LambdaQueryWrapper<Investor>()
                    .eq(Investor::getUserId, userId).last("LIMIT 1"));
            if (i != null && StringUtils.hasText(i.getName())) {
                return i.getName();
            }
        }
        User u = userMapper.selectById(userId);
        if (u != null && StringUtils.hasText(u.getNickName())) {
            return u.getNickName();
        }
        return u != null && StringUtils.hasText(u.getMobile()) ? maskMobile(u.getMobile()) : ("用户" + userId);
    }

    private String resolveIdCard(Long userId, boolean borrowerSide) {
        if (borrowerSide) {
            Borrower b = borrowerMapper.selectOne(new LambdaQueryWrapper<Borrower>()
                    .eq(Borrower::getUserId, userId).last("LIMIT 1"));
            return b != null ? b.getIdCard() : null;
        }
        Investor i = investorMapper.selectOne(new LambdaQueryWrapper<Investor>()
                .eq(Investor::getUserId, userId).last("LIMIT 1"));
        return i != null ? i.getIdCard() : null;
    }

    private Map<String, String> buildVariables(ContractTypeEnum type, Lend lend, Long partyId,
                                               String contractNo, BigDecimal amount) {
        Map<String, String> v = new HashMap<>();
        v.put("contractNo", contractNo);
        v.put("amount", amount == null ? "-" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        v.put("lendNo", lend.getLendNo());
        v.put("lendYearRate", lend.getLendYearRate() == null ? "-" : lend.getLendYearRate().toPlainString());
        v.put("period", lend.getPeriod() == null ? "-" : String.valueOf(lend.getPeriod()));
        v.put("signDate", LocalDateTime.now().format(SIGN_DATE_FMT));
        v.put("purpose", StringUtils.hasText(lend.getLendInfo()) ? lend.getLendInfo() : "个人消费/经营周转");
        v.put("borrowerName", resolveName(lend.getUserId(), true));
        v.put("borrowerIdCard", maskIdCard(resolveIdCard(lend.getUserId(), true)));
        if (type == ContractTypeEnum.INVEST) {
            v.put("investorName", resolveName(partyId, false));
            v.put("investorIdCard", maskIdCard(resolveIdCard(partyId, false)));
        }
        return v;
    }

    private ContractTemplate loadEnabledTemplate(ContractTypeEnum type) {
        return contractTemplateMapper.selectOne(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getContractType, type.getCode())
                .eq(ContractTemplate::getStatus, 1)
                .orderByDesc(ContractTemplate::getVersion)
                .last("LIMIT 1"));
    }

    // ==================== 校验 ====================

    @Override
    public void assertSigned(ContractBizTypeEnum bizType, Long bizId, ContractTypeEnum type, Long partyId,
                             BigDecimal amount) {
        BigDecimal checkAmount = amount != null ? amount : lendAmount(bizId);
        Contract c = findOne(bizType, bizId, type, partyId, checkAmount);
        if (c == null) {
            // 该金额下没有合同，但可能存在"另一个金额的已签合同"——此时要给用户一个能照做的提示
            Contract any = findOne(bizType, bizId, type, partyId, null);
            if (any != null && ContractStatusEnum.SIGNED.getCode().equals(any.getStatus())) {
                throw new ResourceNotFoundException(500,
                        "本次金额 " + checkAmount + " 元与已签署的《" + type.getDesc() + "》金额 "
                                + any.getAmount() + " 元不一致，请按新金额重新签署后再操作");
            }
            throw new ResourceNotFoundException(500,
                    "请先签署《" + type.getDesc() + "》后再继续操作");
        }
        if (ContractStatusEnum.REVOKED.getCode().equals(c.getStatus())) {
            throw new ResourceNotFoundException(500,
                    "《" + type.getDesc() + "》已作废，请联系管理员重新生成后再操作");
        }
        if (!ContractStatusEnum.SIGNED.getCode().equals(c.getStatus())) {
            throw new ResourceNotFoundException(500,
                    "《" + type.getDesc() + "》(编号 " + c.getContractNo() + ") 尚未签署，请先完成签署");
        }
    }

    private BigDecimal lendAmount(Long bizId) {
        Lend lend = lendMapper.selectById(bizId);
        return lend == null ? null : lend.getAmount();
    }

    /** amount 非空时按金额精确定位（金额是合同身份的一部分）；为空则取该业务下最新一份 */
    private Contract findOne(ContractBizTypeEnum bizType, Long bizId, ContractTypeEnum type, Long partyId,
                             BigDecimal amount) {
        LambdaQueryWrapper<Contract> w = new LambdaQueryWrapper<Contract>()
                .eq(Contract::getBizType, bizType.getCode())
                .eq(Contract::getBizId, bizId)
                .eq(Contract::getContractType, type.getCode())
                .eq(Contract::getPartyAId, partyId);
        if (amount != null) {
            w.eq(Contract::getAmount, amount);
        }
        w.orderByDesc(Contract::getId).last("LIMIT 1");
        return this.getOne(w);
    }

    // ==================== 签署 ====================

    @Override
    public Contract sign(Long contractId, Long signerId, String signImgBase64, String ip, String userAgent) {
        Contract c = this.getById(contractId);
        if (c == null) {
            throw new ResourceNotFoundException(500, "合同不存在");
        }
        if (ContractStatusEnum.REVOKED.getCode().equals(c.getStatus())) {
            throw new ResourceNotFoundException(500, "合同已作废，无法签署");
        }

        boolean isPartyA = signerId != null && signerId.equals(c.getPartyAId());
        boolean isPartyB = signerId != null && signerId.equals(c.getPartyBId());
        if (!isPartyA && !isPartyB) {
            throw new ResourceNotFoundException(403, "您不是该合同的签署方，无权签署");
        }
        if (!StringUtils.hasText(signImgBase64)) {
            throw new ResourceNotFoundException(400, "请先完成手写签名");
        }

        // 幂等：同一人重复提交直接返回原合同，不重复写存证（唯一索引 uk_contract_signer 是最终兜底）
        ContractSignRecord old = contractSignRecordMapper.selectOne(new LambdaQueryWrapper<ContractSignRecord>()
                .eq(ContractSignRecord::getContractId, contractId)
                .eq(ContractSignRecord::getSignerId, signerId)
                .last("LIMIT 1"));
        if (old != null) {
            log.info("合同已由该用户签署，幂等返回: contractId={}, signerId={}", contractId, signerId);
            return c;
        }

        // 1) 先落签名图（外部 IO，不能包在事务里，与项目既有做法一致）
        byte[] imgBytes = decodeBase64Image(signImgBase64);
        String imgUrl = fileStorageStrategy.upload(imgBytes, "sign_" + c.getContractNo() + ".png", "signatures");

        // 2) 再写存证 + 更新合同状态。用编程式事务：sign() 内部调用被事务注解的方法属于自调用，
        //    代理不生效会导致事务失效（这是最容易埋的坑），显式 TransactionTemplate 最稳。
        Contract result = new TransactionTemplate(transactionManager)
                .execute(status -> doPersistSign(c, signerId, isPartyA ? "PARTY_A" : "PARTY_B", imgUrl, ip, userAgent));
        Contract signed = result != null ? result : c;

        // 签署成功通知：必须放在 TransactionTemplate 之外发送。
        // 写在事务里的话，事务回滚时消息已经通过 Feign/站内信发出去了，
        // 用户会收到一条"签署成功"的假消息 —— 这是最难排查的一类脏数据。
        try {
            ContractTypeEnum typeEnum = ContractTypeEnum.fromCode(signed.getContractType());
            messageService.sendContractSignNotice(signerId,
                    typeEnum != null ? typeEnum.getDesc() : "电子合同", signed.getContractNo());
        } catch (Exception e) {
            // 通知失败不影响签署本身
            log.warn("合同签署通知发送失败，contractId={}", contractId, e);
        }

        return signed;
    }

    private Contract doPersistSign(Contract c, Long signerId, String role, String imgUrl, String ip, String userAgent) {
        ContractSignRecord r = new ContractSignRecord();
        r.setContractId(c.getId());
        r.setSignerId(signerId);
        r.setSignerRole(role);
        r.setSignerName("PARTY_A".equals(role) ? c.getPartyAName() : c.getPartyBName());
        r.setSignerIdCard("PARTY_A".equals(role)
                ? resolveIdCard(c.getPartyAId(), false)
                : resolveIdCard(c.getPartyBId(), true));
        r.setSignImgUrl(imgUrl);
        // 存证里记录"签署这一刻"的正文哈希，日后与快照重算结果比对即可发现篡改
        r.setContentHash(sha256(c.getContentSnapshot()));
        r.setSignIp(ip);
        r.setUserAgent(userAgent);
        r.setSignTime(LocalDateTime.now());
        contractSignRecordMapper.insert(r);

        c.setStatus(ContractStatusEnum.SIGNED.getCode());
        c.setUpdateTime(LocalDateTime.now());
        this.updateById(c);
        log.info("合同签署成功: no={}, signerId={}, role={}, img={}", c.getContractNo(), signerId, role, imgUrl);
        return c;
    }

    // ==================== 查询 / 作废 ====================

    @Override
    public Page<Contract> pageMy(Long userId, String contractType, Integer status, Integer pageNum, Integer pageSize) {
        Page<Contract> page = PageUtils.of(pageNum, pageSize);
        LambdaQueryWrapper<Contract> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(contractType), Contract::getContractType, contractType);
        w.eq(status != null, Contract::getStatus, status);
        w.and(x -> x.eq(Contract::getPartyAId, userId).or().eq(Contract::getPartyBId, userId));
        w.orderByDesc(Contract::getCreateTime);
        return this.page(page, w);
    }

    @Override
    public Map<String, Object> detail(Long contractId, Long userId) {
        Contract c = this.getById(contractId);
        if (c == null) {
            throw new ResourceNotFoundException(500, "合同不存在");
        }
        if (userId != null && !userId.equals(c.getPartyAId()) && !userId.equals(c.getPartyBId())) {
            throw new ResourceNotFoundException(403, "无权查看该合同");
        }
        ContractSignRecord r = contractSignRecordMapper.selectOne(new LambdaQueryWrapper<ContractSignRecord>()
                .eq(ContractSignRecord::getContractId, contractId)
                .orderByDesc(ContractSignRecord::getId)
                .last("LIMIT 1"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contract", c);
        data.put("content", c.getContentSnapshot());
        data.put("signRecord", r);
        // 哈希自检：能证明正文自签署后未被改动
        boolean hashValid = r == null
                || sha256(c.getContentSnapshot()).equals(r.getContentHash());
        data.put("hashValid", hashValid);
        return data;
    }

    @Override
    public void revoke(Long contractId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new ResourceNotFoundException(400, "作废原因不能为空");
        }
        Contract c = this.getById(contractId);
        if (c == null) {
            throw new ResourceNotFoundException(500, "合同不存在");
        }
        if (!ContractStatusEnum.PENDING.getCode().equals(c.getStatus())) {
            throw new ResourceNotFoundException(500, "只有待签署的合同才能作废");
        }
        c.setStatus(ContractStatusEnum.REVOKED.getCode());
        c.setRevokeReason(reason);
        c.setUpdateTime(LocalDateTime.now());
        this.updateById(c);
        log.info("合同已作废: no={}, reason={}", c.getContractNo(), reason);
    }

    // ==================== 补建历史缺失合同 ====================

    @Override
    public Map<String, Object> syncMyContracts(Long userId) {
        Map<String, Object> stat = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        int processed = 0;
        int skipped = 0;
        if (userId == null) {
            stat.put("created", 0);
            stat.put("processed", 0);
            stat.put("skipped", 0);
            stat.put("notes", notes);
            return stat;
        }
        // 用"同步前后我的合同条数之差"来判断真正新建了几份
        // （getOrCreate 对已存在的合同是幂等返回，光看返回值分不出新建还是复用）
        long before = countMyContracts(userId);

        // ① 我是借款人的标的（待发布/募资中）→《借款合同》；募资中的再补《放款确认书》
        List<Lend> myLends = lendMapper.selectList(new LambdaQueryWrapper<Lend>()
                .eq(Lend::getUserId, userId)
                .in(Lend::getStatus, LendStatusEnum.PENDING.getCode(), LendStatusEnum.FUNDING.getCode()));
        for (Lend lend : myLends) {
            try {
                if (getOrCreate(ContractBizTypeEnum.LEND, lend.getId(),
                        ContractTypeEnum.LOAN, userId) != null) {
                    processed++;
                }
                if (LendStatusEnum.FUNDING.getCode().equals(lend.getStatus())
                        && getOrCreate(ContractBizTypeEnum.LEND, lend.getId(),
                        ContractTypeEnum.PAYMENT, userId) != null) {
                    processed++;
                }
            } catch (Exception e) {
                skipped++;
                notes.add("标的 " + lend.getId() + " 借款/放款合同补建失败：" + e.getMessage());
                log.warn("补建借款合同失败: lendId={}, userId={}", lend.getId(), userId, e);
            }
        }

        // ② 我是投资人的投资记录 →《出借协议》，金额取该笔投资额
        //   （必须与投资额一致，否则下单时金额比对不通过）
        List<LendItem> myItems = lendItemMapper.selectList(new LambdaQueryWrapper<LendItem>()
                .eq(LendItem::getInvestUserId, userId)
                .ne(LendItem::getStatus, 1));
        for (LendItem item : myItems) {
            try {
                if (getOrCreate(ContractBizTypeEnum.LEND, item.getLendId(),
                        ContractTypeEnum.INVEST, userId, item.getInvestAmount()) != null) {
                    processed++;
                }
            } catch (Exception e) {
                skipped++;
                notes.add("投资记录 " + item.getId() + " 出借协议补建失败：" + e.getMessage());
                log.warn("补建出借协议失败: lendItemId={}, userId={}", item.getId(), userId, e);
            }
        }

        long after = countMyContracts(userId);
        stat.put("created", Math.max(0, after - before));
        stat.put("processed", processed);
        stat.put("skipped", skipped);
        stat.put("notes", notes);
        log.info("合同补建完成: userId={}, created={}, processed={}, skipped={}",
                userId, after - before, processed, skipped);
        return stat;
    }

    /** 当前用户作为甲方或乙方的合同条数 */
    private long countMyContracts(Long userId) {
        return this.count(new LambdaQueryWrapper<Contract>()
                .and(w -> w.eq(Contract::getPartyAId, userId).or().eq(Contract::getPartyBId, userId)));
    }

    // ==================== 工具 ====================

    private byte[] decodeBase64Image(String base64) {
        String s = base64;
        int comma = s.indexOf(',');
        if (s.startsWith("data:") && comma > 0) {
            s = s.substring(comma + 1);
        }
        s = s.replaceAll("\\s", "");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException(400, "签名图片格式错误，请重新签名");
        }
        if (bytes.length == 0) {
            throw new ResourceNotFoundException(400, "签名图片为空，请重新签名");
        }
        if (bytes.length > SIGN_IMG_MAX_BYTES) {
            throw new ResourceNotFoundException(400, "签名图片过大，请重新签名");
        }
        return bytes;
    }

    private static String render(String tpl, Map<String, String> vars) {
        String out = tpl;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", e.getValue() == null ? "-" : e.getValue());
        }
        // 模板里声明了但业务没提供的变量，统一落到 "-"，避免正文出现 ${xxx} 原样
        return out.replaceAll("\\$\\{[A-Za-z0-9_]+}", "-");
    }

    private static String sha256(String text) {
        return ContractHashUtils.sha256(text);
    }

    private static String genContractNo() {
        return "HT" + LocalDateTime.now().format(NO_FMT) + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private static String maskIdCard(String idCard) {
        if (!StringUtils.hasText(idCard) || idCard.length() < 8) {
            return StringUtils.hasText(idCard) ? idCard : "-";
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    private static String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile) || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
