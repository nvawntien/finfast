package finfast.architecture.fixtures.accounts.api.dto;

import com.finfast.common.money.VndAmount;

public record AccountView(String accountNumber, VndAmount balanceVnd) {
}
