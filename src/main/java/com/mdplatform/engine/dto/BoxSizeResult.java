package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 盒子尺寸计算结果DTO - 用于返回模拟盒子尺寸的计算结果
 *
 * <p>功能：
 *     1. 封装模拟盒子的三维尺寸和体积信息
 *     2. 根据分子数量和密度计算得到
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxSizeResult {

    /** X方向盒子尺寸（埃） */
    private Double x;

    /** Y方向盒子尺寸（埃） */
    private Double y;

    /** Z方向盒子尺寸（埃） */
    private Double z;

    /** 盒子体积（立方埃） */
    private Double volume;
}
