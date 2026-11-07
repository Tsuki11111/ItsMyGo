package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.report.ConsumptionMonthlyRow;
import com.tsuk1.itsmygo.dto.report.ConsumptionRankRow;
import com.tsuk1.itsmygo.dto.report.ConsumptionReportView;
import com.tsuk1.itsmygo.dto.report.ConsumptionSummaryRow;
import com.tsuk1.itsmygo.mapper.ReportMapper;
import com.tsuk1.itsmygo.service.ConsumptionReportService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 消费报告聚合实现：期间解析 + DB 聚合 + 金额格式化。
 *
 * <p>「已支付订单」= `paid_at` 落在期间内且状态属于
 * `PAID/SHIPPED/COMPLETED/REFUNDING/REFUNDED`。退款按 `orders.refunded_amount` 扣减，
 * 不做利息/税费，也不推算任何金额。</p>
 */
@Service
public class ConsumptionReportServiceImpl implements ConsumptionReportService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int TOP_N = 3;
    private static final Pattern MONTH_PATTERN = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");

    private final ReportMapper reportMapper;

    public ConsumptionReportServiceImpl(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @Override
    public ConsumptionReportView report(long userId, String period, String date) {
        String cleanPeriod = period == null || period.isBlank()
                ? "month" : period.trim().toLowerCase(Locale.ROOT);
        Range range = switch (cleanPeriod) {
            case "month" -> monthRange(date);
            case "year" -> yearRange(date);
            default -> throw new BizException(ResultCode.BAD_REQUEST,
                    "period 只支持 month 或 year");
        };

        ConsumptionSummaryRow summary = reportMapper.summarize(userId, range.start(), range.end());
        BigDecimal paid = amount(summary == null ? null : summary.getPaidAmount());
        BigDecimal refunded = amount(summary == null ? null : summary.getRefundedAmount());
        BigDecimal discount = amount(summary == null ? null : summary.getDiscountAmount());
        long orderCount = summary == null || summary.getOrderCount() == null
                ? 0 : summary.getOrderCount();

        BigDecimal avg = orderCount == 0
                ? BigDecimal.ZERO
                : paid.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

        List<ConsumptionReportView.RankItem> categories = reportMapper
                .topCategories(userId, range.start(), range.end(), TOP_N).stream()
                .map(row -> new ConsumptionReportView.RankItem(
                        row.getName(), money(row.getAmount()), count(row.getCount())))
                .toList();
        List<ConsumptionReportView.ShopItem> merchants = reportMapper
                .topMerchants(userId, range.start(), range.end(), TOP_N).stream()
                .map(row -> new ConsumptionReportView.ShopItem(
                        row.getName(), money(row.getAmount())))
                .toList();
        List<ConsumptionReportView.RankItem> topProducts = reportMapper
                .topProducts(userId, range.start(), range.end(), TOP_N).stream()
                .map(row -> new ConsumptionReportView.RankItem(
                        row.getName(), money(row.getAmount()), count(row.getCount())))
                .toList();
        List<ConsumptionReportView.MonthlyItem> monthly = range.yearly()
                ? reportMapper.monthly(userId, range.start(), range.end()).stream()
                        .map(row -> new ConsumptionReportView.MonthlyItem(
                                row.getMonth(), money(row.getAmount())))
                        .toList()
                : List.of();

        return new ConsumptionReportView(
                range.label(), orderCount,
                money(paid), money(refunded), money(paid.subtract(refunded)),
                money(discount), money(avg),
                categories, merchants, topProducts, monthly);
    }

    private Range monthRange(String date) {
        YearMonth yearMonth;
        if (date == null || date.isBlank()) {
            yearMonth = YearMonth.now(SHANGHAI);
        } else {
            String clean = date.trim();
            if (!MONTH_PATTERN.matcher(clean).matches()) {
                throw new BizException(ResultCode.BAD_REQUEST, "日期格式应为 YYYY-MM");
            }
            try {
                yearMonth = YearMonth.parse(clean);
            } catch (DateTimeParseException e) {
                throw new BizException(ResultCode.BAD_REQUEST, "日期格式应为 YYYY-MM");
            }
        }
        return new Range(
                yearMonth.atDay(1).atStartOfDay(),
                yearMonth.plusMonths(1).atDay(1).atStartOfDay(),
                yearMonth.getYear() + "年" + yearMonth.getMonthValue() + "月",
                false);
    }

    private Range yearRange(String date) {
        int year;
        if (date == null || date.isBlank()) {
            year = LocalDate.now(SHANGHAI).getYear();
        } else {
            String clean = date.trim();
            if (!YEAR_PATTERN.matcher(clean).matches()) {
                throw new BizException(ResultCode.BAD_REQUEST, "日期格式应为 YYYY");
            }
            year = Integer.parseInt(clean);
        }
        return new Range(
                LocalDate.of(year, 1, 1).atStartOfDay(),
                LocalDate.of(year + 1, 1, 1).atStartOfDay(),
                year + "年",
                true);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static long count(Long value) {
        return value == null ? 0 : value;
    }

    /** 金额统一两位小数字符串，避免前端浮点误差。 */
    private static String money(BigDecimal value) {
        return amount(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 解析后的查询区间：左闭右开，避免月末/年末边界漏单或重复。 */
    private record Range(LocalDateTime start, LocalDateTime end, String label, boolean yearly) {
    }
}
