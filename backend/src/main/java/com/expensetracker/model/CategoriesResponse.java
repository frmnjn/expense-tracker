package com.expensetracker.model;

import java.util.List;

public record CategoriesResponse(List<CategoryOption> categories) {
}
