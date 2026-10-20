package com.ganchevdimitarg.catalog.dto.category;

import java.util.UUID;

public record CategoryResponseDto(UUID id, String name, long productCount) {
}
