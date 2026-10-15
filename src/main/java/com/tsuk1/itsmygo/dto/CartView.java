package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartView(List<CartItemView> items, BigDecimal selectedTotal) {
}
