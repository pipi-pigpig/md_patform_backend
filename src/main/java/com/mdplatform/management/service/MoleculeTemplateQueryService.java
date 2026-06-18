package com.mdplatform.management.service;

import com.mdplatform.management.dto.MoleculeSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 分子模板查询服务（对接 engine 模块的 MoleculeTemplateService）。
 * <p>
 * 真实实现由另一方在 engine 包中提供；本方通过 Mock 实现进行联调，
 * 待 engine 方接入后切换为真实实现（@Profile("!mock")）。
 */
public interface MoleculeTemplateQueryService {

    /**
     * 按分子名称查询模板快照。
     */
    Optional<MoleculeSnapshot> findByName(String name);

    /**
     * 列出所有系统模板名称（用于前端下拉提示，可选实现）。
     */
    List<String> systemTemplateNames();
}
