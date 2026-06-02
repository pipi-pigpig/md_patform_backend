package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxSizeResult {

    private Double x;

    private Double y;

    private Double z;

    private Double volume;
}