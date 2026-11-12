package cn.edu.buaa.ledger.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Shared category codes and labels. Keep codes stable: they are stored in Room. */
public final class CategoryCatalog {
    private CategoryCatalog() { }

    public static final class Category {
        public final String code, name, type;
        private Category(String code, String name, String type) {
            this.code = code; this.name = name; this.type = type;
        }
    }

    public static final List<Category> ALL = Collections.unmodifiableList(Arrays.asList(
        new Category("food", "餐饮", "expense"), new Category("shopping", "购物", "expense"),
        new Category("transport", "交通", "expense"), new Category("study", "学习", "expense"),
        new Category("bills", "生活缴费", "expense"), new Category("other", "其他支出", "expense"),
        new Category("allowance", "生活费", "income"), new Category("scholarship", "奖助学金", "income"),
        new Category("work", "兼职", "income"), new Category("other_income", "其他收入", "income")
    ));

    public static String[] names(String type) {
        return ALL.stream().filter(c -> c.type.equals(type)).map(c -> c.name).toArray(String[]::new);
    }
}
