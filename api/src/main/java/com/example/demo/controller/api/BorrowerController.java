package com.example.demo.controller.api;

import com.example.demo.entity.Borrower;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.BorrowerAuthDto;
import com.example.demo.service.BorrowerService;
import com.example.demo.service.UserService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 实名认证（借款人档案）
 * 流程：提交认证（认证中）→ 平台审核（通过/拒绝）→ 通过后成为借款人身份
 * 说明：ww_bank 模拟银行无实名认证接口，四要素由绑卡环节银行端校验；
 *       本模块做姓名/身份证格式/手机号一致性本地校验；人脸识别预留（TODO）
 */
@Api(tags = "实名认证")
@RestController
@RequestMapping("/api/core/borrower")
public class BorrowerController {

    /** 18位身份证号正则（末位可为X） */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");

    @Autowired
    private BorrowerService borrowerService;

    @Autowired
    private UserService userService;

    @ApiOperation("提交实名认证")
    @PostMapping("/auth/submit")
    public PccAjaxResult submit(@RequestBody BorrowerAuthDto dto) {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        User user = userService.getById(userId);
        if (user == null) {
            return new PccAjaxResult(500, "用户不存在");
        }
        // 校验：姓名非空
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            return new PccAjaxResult(500, "姓名不能为空");
        }
        // 校验：身份证号 18 位格式
        if (dto.getIdCard() == null || !ID_CARD_PATTERN.matcher(dto.getIdCard()).matches()) {
            return new PccAjaxResult(500, "身份证号格式错误");
        }
        // 校验：身份证正反面图片 URL 非空
        if (dto.getIdCardFrontUrl() == null || dto.getIdCardFrontUrl().trim().isEmpty()
                || dto.getIdCardBackUrl() == null || dto.getIdCardBackUrl().trim().isEmpty()) {
            return new PccAjaxResult(500, "请先上传身份证正反面照片");
        }
        // 校验：重复提交
        if (borrowerService.getByUserId(userId) != null) {
            return new PccAjaxResult(500, "已提交过认证，请勿重复提交");
        }
        // TODO 四要素校验：姓名+身份证+手机号一致性（本地校验）；人脸识别：预留第三方能力
        Long borrowerId = borrowerService.submitAuth(userId, dto);
        return new PccAjaxResult(200, "认证资料已提交，等待审核", borrowerId);
    }

    @ApiOperation("审核实名认证（平台）")
    @PostMapping("/auth/approve")
    public PccAjaxResult approve(@RequestParam Long borrowerId, @RequestParam boolean approved) {
        Borrower borrower = borrowerService.getById(borrowerId);
        if (borrower == null) {
            return new PccAjaxResult(500, "认证记录不存在");
        }
        if (borrower.getStatus() != 1) {
            return new PccAjaxResult(500, "仅认证中状态可审核");
        }
        borrowerService.approveAuth(borrowerId, approved);
        return new PccAjaxResult(200, approved ? "认证通过，已成为借款人" : "认证已拒绝");
    }

    @ApiOperation("查询当前用户认证状态")
    @GetMapping("/auth/status")
    public PccAjaxResult status() {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        Borrower borrower = borrowerService.getByUserId(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("borrowAuthStatus", userService.getById(userId).getBorrowAuthStatus());
        data.put("borrower", borrower);
        return new PccAjaxResult(200, "查询成功", data);
    }
}
