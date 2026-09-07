package  com.example.demo.entity.dto;

import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.UserBind;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AccountInfoDto {
    private Long userId;
    private String mobile;
    private String name;
    private String idCard;
    private Integer userType;
    private Integer borrowAuthStatus; // 实名认证状态
    private BigDecimal amount; // 可用余额
    private BigDecimal freezeAmount; // 冻结金额
    private Integer bindStatus; // 绑卡状态
    private String bindCode; // 银行卡号
}