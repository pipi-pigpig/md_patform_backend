package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.MoleculeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分子模板数据访问层，提供分子模板的CRUD操作和自定义查询
 *
 * <p>功能：
 *     1. 根据分子名称、分子类型查询分子模板
 *     2. 查询系统预置分子模板
 *     3. 根据创建用户查询自定义分子模板
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface MoleculeTemplateRepository extends JpaRepository<MoleculeTemplate, Long> {

    /**
     * 根据分子名称查询分子模板
     *
     * <p>分子名称具有唯一性约束，最多返回一条记录</p>
     *
     * @param moleculeName 分子名称（如EC、DMC、LiPF6等）
     * @return 匹配的分子模板，若不存在则返回空的Optional
     */
    Optional<MoleculeTemplate> findByMoleculeName(String moleculeName);

    /**
     * 根据分子类型查询分子模板列表
     *
     * @param moleculeType 分子类型（如solvent、salt、additive等）
     * @return 指定类型的分子模板列表
     */
    List<MoleculeTemplate> findByMoleculeType(String moleculeType);

    /**
     * 查询所有系统预置分子模板
     *
     * <p>系统预置模板的isSystemTemplate字段为true，所有用户共享使用</p>
     *
     * @return 系统预置的分子模板列表
     */
    List<MoleculeTemplate> findByIsSystemTemplateTrue();

    /**
     * 根据创建用户ID查询自定义分子模板列表
     *
     * @param createUserId 创建用户ID
     * @return 该用户创建的所有自定义分子模板列表
     */
    List<MoleculeTemplate> findByCreateUserId(Long createUserId);
}