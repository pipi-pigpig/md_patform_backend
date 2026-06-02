"""
文件操作工具模块

提供文件读写、模板复制等功能
"""

import json
import shutil
import logging
import os
from pathlib import Path
from typing import Dict, Any, Optional

logger = logging.getLogger(__name__)


def read_formula_json(filepath: str) -> Dict[str, Any]:
    """读取配方JSON文件
    
    Args:
        filepath: JSON文件路径
        
    Returns:
        配方数据字典
        
    Raises:
        FileNotFoundError: 文件不存在
        json.JSONDecodeError: JSON解析错误
    """
    filepath = Path(filepath)
    
    if not filepath.exists():
        raise FileNotFoundError(f"配方文件不存在: {filepath}")
    
    logger.info(f"读取配方文件: {filepath}")
    
    with open(filepath, "r", encoding="utf-8") as f:
        data = json.load(f)
    
    logger.debug(f"配方数据: {data}")
    return data


def write_result_json(filepath: str, result: Dict[str, Any]) -> None:
    """写入结果JSON文件
    
    Args:
        filepath: 输出文件路径
        result: 结果数据字典
        
    Raises:
        IOError: 写入失败
    """
    filepath = Path(filepath)
    filepath.parent.mkdir(parents=True, exist_ok=True)
    
    temp_filepath = filepath.with_suffix(".tmp")
    
    try:
        with open(temp_filepath, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        
        if filepath.exists():
            filepath.unlink()
        
        temp_filepath.rename(filepath)
        logger.info(f"结果已写入: {filepath}")
        
    except Exception as e:
        if temp_filepath.exists():
            temp_filepath.unlink()
        raise IOError(f"写入结果文件失败: {e}")


def copy_template_files(source: str, dest: str) -> None:
    """复制模板文件
    
    Args:
        source: 源目录路径
        dest: 目标目录路径
        
    Raises:
        FileNotFoundError: 源目录不存在
        IOError: 复制失败
    """
    source_path = Path(source)
    dest_path = Path(dest)
    
    if not source_path.exists():
        raise FileNotFoundError(f"模板目录不存在: {source_path}")
    
    dest_path.mkdir(parents=True, exist_ok=True)
    
    logger.info(f"复制模板文件: {source_path} -> {dest_path}")
    
    for item in source_path.iterdir():
        dest_item = dest_path / item.name
        if item.is_file():
            shutil.copy2(item, dest_item)
            logger.debug(f"复制文件: {item.name}")
        elif item.is_dir():
            shutil.copytree(item, dest_item, dirs_exist_ok=True)
            logger.debug(f"复制目录: {item.name}")


def ensure_directory(dirpath: str) -> Path:
    """确保目录存在
    
    Args:
        dirpath: 目录路径
        
    Returns:
        Path对象
    """
    path = Path(dirpath)
    path.mkdir(parents=True, exist_ok=True)
    logger.debug(f"确保目录存在: {path}")
    return path


def read_json(filepath: str) -> Dict[str, Any]:
    """读取JSON文件
    
    Args:
        filepath: JSON文件路径
        
    Returns:
        数据字典
    """
    filepath = Path(filepath)
    
    if not filepath.exists():
        raise FileNotFoundError(f"JSON文件不存在: {filepath}")
    
    with open(filepath, "r", encoding="utf-8") as f:
        return json.load(f)


def write_json(filepath: str, data: Dict[str, Any]) -> None:
    """写入JSON文件
    
    Args:
        filepath: 输出文件路径
        data: 数据字典
    """
    filepath = Path(filepath)
    filepath.parent.mkdir(parents=True, exist_ok=True)
    
    temp_filepath = filepath.with_suffix(".tmp")
    
    try:
        with open(temp_filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        
        if filepath.exists():
            filepath.unlink()
        
        temp_filepath.rename(filepath)
        
    except Exception as e:
        if temp_filepath.exists():
            temp_filepath.unlink()
        raise IOError(f"写入JSON文件失败: {e}")


def file_exists(filepath: str) -> bool:
    """检查文件是否存在
    
    Args:
        filepath: 文件路径
        
    Returns:
        是否存在
    """
    return Path(filepath).exists()


def delete_file(filepath: str) -> bool:
    """删除文件
    
    Args:
        filepath: 文件路径
        
    Returns:
        是否删除成功
    """
    path = Path(filepath)
    if path.exists():
        path.unlink()
        logger.debug(f"删除文件: {filepath}")
        return True
    return False


def get_file_size(filepath: str) -> Optional[int]:
    """获取文件大小
    
    Args:
        filepath: 文件路径
        
    Returns:
        文件大小(字节)，文件不存在返回None
    """
    path = Path(filepath)
    if path.exists():
        return path.stat().st_size
    return None


def list_files(directory: str, pattern: str = "*") -> list:
    """列出目录中的文件
    
    Args:
        directory: 目录路径
        pattern: 文件匹配模式
        
    Returns:
        文件路径列表
    """
    dir_path = Path(directory)
    if not dir_path.exists():
        return []
    
    return [str(f) for f in dir_path.glob(pattern) if f.is_file()]