package com.mdplatform.common.util;

import com.mdplatform.common.config.StorageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PathUtilTest {

    @Mock
    private StorageConfig storageConfig;

    @TempDir
    Path tempDir;

    private PathUtil pathUtil;

    @BeforeEach
    void setUp() {
        lenient().when(storageConfig.getRootPath()).thenReturn(tempDir.toString());
        pathUtil = new PathUtil(storageConfig);
    }

    @Test
    @DisplayName("测试获取任务根路径 - 验证格式 user_{userId}/jobs/job_{jobId}/")
    void testGetJobRootPath() {
        Long userId = 1L;
        Long jobId = 100L;

        Path result = pathUtil.getJobRootPath(userId, jobId);

        String pathString = result.toString();
        assertTrue(pathString.contains("user_1"), "路径应包含 user_1");
        assertTrue(pathString.contains("jobs"), "路径应包含 jobs");
        assertTrue(pathString.contains("job_100"), "路径应包含 job_100");
        assertTrue(pathString.endsWith("job_100") || pathString.endsWith("job_100" + System.getProperty("file.separator")),
                "路径应以 job_100 结尾");
    }

    @Test
    @DisplayName("测试获取输入目录路径 - 验证格式包含 inputs/")
    void testGetInputPath() {
        Long userId = 1L;
        Long jobId = 100L;

        Path result = pathUtil.getInputPath(userId, jobId);

        String pathString = result.toString();
        assertTrue(pathString.contains("inputs"), "路径应包含 inputs");
        assertTrue(pathString.contains("user_1"), "路径应包含用户目录");
        assertTrue(pathString.contains("job_100"), "路径应包含任务目录");
    }

    @Test
    @DisplayName("测试获取输出目录路径 - 验证格式包含 raw_outputs/")
    void testGetOutputPath() {
        Long userId = 1L;
        Long jobId = 100L;

        Path result = pathUtil.getOutputPath(userId, jobId);

        String pathString = result.toString();
        assertTrue(pathString.contains("raw_outputs"), "路径应包含 raw_outputs");
        assertTrue(pathString.contains("user_1"), "路径应包含用户目录");
        assertTrue(pathString.contains("job_100"), "路径应包含任务目录");
    }

    @Test
    @DisplayName("测试获取后处理目录路径 - 验证格式包含 post_processing/")
    void testGetPostProcessingPath() {
        Long userId = 1L;
        Long jobId = 100L;

        Path result = pathUtil.getPostProcessingPath(userId, jobId);

        String pathString = result.toString();
        assertTrue(pathString.contains("post_processing"), "路径应包含 post_processing");
        assertTrue(pathString.contains("user_1"), "路径应包含用户目录");
        assertTrue(pathString.contains("job_100"), "路径应包含任务目录");
    }

    @Test
    @DisplayName("测试获取临时文件目录路径 - 验证格式包含 temp/")
    void testGetTempPath() {
        Long userId = 1L;
        Long jobId = 100L;

        Path result = pathUtil.getTempPath(userId, jobId);

        String pathString = result.toString();
        assertTrue(pathString.contains("temp"), "路径应包含 temp");
        assertTrue(pathString.contains("user_1"), "路径应包含用户目录");
        assertTrue(pathString.contains("job_100"), "路径应包含任务目录");
    }

    @Test
    @DisplayName("测试获取可视化目录路径 - 验证格式包含 visualization/")
    void testGetVisualizationPath() {
        Long userId = 1L;
        Long jobId = 100L;

        Path result = pathUtil.getVisualizationPath(userId, jobId);

        String pathString = result.toString();
        assertTrue(pathString.contains("visualization"), "路径应包含 visualization");
        assertTrue(pathString.contains("user_1"), "路径应包含用户目录");
        assertTrue(pathString.contains("job_100"), "路径应包含任务目录");
    }

    @Test
    @DisplayName("测试获取报告目录路径 - 验证格式包含 report/")
    void testGetReportPath() {
        Long userId = 1L;
        Long jobId = 100L;

        Path result = pathUtil.getReportPath(userId, jobId);

        String pathString = result.toString();
        assertTrue(pathString.contains("report"), "路径应包含 report");
        assertTrue(pathString.contains("user_1"), "路径应包含用户目录");
        assertTrue(pathString.contains("job_100"), "路径应包含任务目录");
    }

    @Test
    @DisplayName("测试生成LAMMPS脚本文件名 - 验证格式 in.{stage}")
    void testGetLammpsScriptFilename() {
        String stage = "production";

        String result = pathUtil.getLammpsScriptFilename(stage);

        assertEquals("in.production", result, "LAMMPS脚本文件名应为 in.production");
    }

    @Test
    @DisplayName("测试生成LAMMPS脚本文件名 - 大写转小写")
    void testGetLammpsScriptFilenameUpperCase() {
        String stage = "EQUILIBRATION";

        String result = pathUtil.getLammpsScriptFilename(stage);

        assertEquals("in.equilibration", result, "LAMMPS脚本文件名应转换为小写");
    }

    @Test
    @DisplayName("测试生成LAMMPS脚本文件名 - 特殊字符过滤")
    void testGetLammpsScriptFilenameSpecialChars() {
        String stage = "min-imization";

        String result = pathUtil.getLammpsScriptFilename(stage);

        assertEquals("in.minimization", result, "特殊字符应被过滤");
    }

    @Test
    @DisplayName("测试生成轨迹文件名 - 验证格式 dump.{type}.lammpstrj")
    void testGetTrajectoryFilename() {
        String type = "trajectory";

        String result = pathUtil.getTrajectoryFilename(type);

        assertEquals("dump.trajectory.lammpstrj", result, "轨迹文件名应为 dump.trajectory.lammpstrj");
    }

    @Test
    @DisplayName("测试生成轨迹文件名 - 大写转小写")
    void testGetTrajectoryFilenameUpperCase() {
        String type = "VELOCITY";

        String result = pathUtil.getTrajectoryFilename(type);

        assertEquals("dump.velocity.lammpstrj", result, "轨迹文件名应转换为小写");
    }

    @Test
    @DisplayName("测试生成结果文件名 - 验证格式 {property}_result.json")
    void testGetResultFilename() {
        String property = "density";

        String result = pathUtil.getResultFilename(property);

        assertEquals("density_result.json", result, "结果文件名应为 density_result.json");
    }

    @Test
    @DisplayName("测试生成结果文件名 - 大写转小写")
    void testGetResultFilenameUpperCase() {
        String property = "CONDUCTIVITY";

        String result = pathUtil.getResultFilename(property);

        assertEquals("conductivity_result.json", result, "结果文件名应转换为小写");
    }

    @Test
    @DisplayName("测试生成临时文件名 - 验证格式 job_{jobId}_{name}.tmp")
    void testGetTempFilename() {
        Long jobId = 100L;
        String name = "calculation";

        String result = pathUtil.getTempFilename(jobId, name);

        assertEquals("job_100_calculation.tmp", result, "临时文件名应为 job_100_calculation.tmp");
    }

    @Test
    @DisplayName("测试生成临时文件名 - 大写转小写")
    void testGetTempFilenameUpperCase() {
        Long jobId = 200L;
        String name = "TEMP_DATA";

        String result = pathUtil.getTempFilename(jobId, name);

        assertEquals("job_200_temp_data.tmp", result, "临时文件名应转换为小写");
    }

    @Test
    @DisplayName("测试参数校验 - userId为null时抛出异常")
    void testNullUserIdThrowsException() {
        Long jobId = 100L;

        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            pathUtil.getJobRootPath(null, jobId);
        });

        assertEquals("userId不能为null", exception.getMessage());
    }

    @Test
    @DisplayName("测试参数校验 - jobId为null时抛出异常")
    void testNullJobIdThrowsException() {
        Long userId = 1L;

        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            pathUtil.getJobRootPath(userId, null);
        });

        assertEquals("jobId不能为null", exception.getMessage());
    }

    @Test
    @DisplayName("测试参数校验 - userId为负数时抛出异常")
    void testNegativeUserIdThrowsException() {
        Long userId = -1L;
        Long jobId = 100L;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pathUtil.getJobRootPath(userId, jobId);
        });

        assertEquals("userId必须大于0", exception.getMessage());
    }

    @Test
    @DisplayName("测试参数校验 - userId为0时抛出异常")
    void testZeroUserIdThrowsException() {
        Long userId = 0L;
        Long jobId = 100L;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pathUtil.getJobRootPath(userId, jobId);
        });

        assertEquals("userId必须大于0", exception.getMessage());
    }

    @Test
    @DisplayName("测试参数校验 - jobId为负数时抛出异常")
    void testNegativeJobIdThrowsException() {
        Long userId = 1L;
        Long jobId = -1L;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pathUtil.getJobRootPath(userId, jobId);
        });

        assertEquals("jobId必须大于0", exception.getMessage());
    }

    @Test
    @DisplayName("测试目录创建 - 验证所有子目录创建成功")
    void testCreateJobDirectories() {
        Long userId = 1L;
        Long jobId = 100L;

        pathUtil.createJobDirectories(userId, jobId);

        Path jobRoot = pathUtil.getJobRootPath(userId, jobId);
        assertTrue(Files.exists(jobRoot), "任务根目录应存在");
        assertTrue(Files.isDirectory(jobRoot), "任务根路径应为目录");

        Path inputPath = pathUtil.getInputPath(userId, jobId);
        assertTrue(Files.exists(inputPath), "输入目录应存在");
        assertTrue(Files.isDirectory(inputPath), "输入路径应为目录");

        Path outputPath = pathUtil.getOutputPath(userId, jobId);
        assertTrue(Files.exists(outputPath), "输出目录应存在");
        assertTrue(Files.isDirectory(outputPath), "输出路径应为目录");

        Path postProcessingPath = pathUtil.getPostProcessingPath(userId, jobId);
        assertTrue(Files.exists(postProcessingPath), "后处理目录应存在");
        assertTrue(Files.isDirectory(postProcessingPath), "后处理路径应为目录");

        Path tempPath = pathUtil.getTempPath(userId, jobId);
        assertTrue(Files.exists(tempPath), "临时文件目录应存在");
        assertTrue(Files.isDirectory(tempPath), "临时文件路径应为目录");

        Path visualizationPath = pathUtil.getVisualizationPath(userId, jobId);
        assertTrue(Files.exists(visualizationPath), "可视化目录应存在");
        assertTrue(Files.isDirectory(visualizationPath), "可视化路径应为目录");

        Path reportPath = pathUtil.getReportPath(userId, jobId);
        assertTrue(Files.exists(reportPath), "报告目录应存在");
        assertTrue(Files.isDirectory(reportPath), "报告路径应为目录");
    }

    @Test
    @DisplayName("测试目录创建 - 多次调用不会出错")
    void testCreateJobDirectoriesIdempotent() {
        Long userId = 1L;
        Long jobId = 200L;

        pathUtil.createJobDirectories(userId, jobId);
        pathUtil.createJobDirectories(userId, jobId);

        Path jobRoot = pathUtil.getJobRootPath(userId, jobId);
        assertTrue(Files.exists(jobRoot), "任务根目录应存在");
    }

    @Test
    @DisplayName("测试路径校验 - 路径存在且可读返回true")
    void testValidatePathExists() {
        Long userId = 1L;
        Long jobId = 300L;
        pathUtil.createJobDirectories(userId, jobId);

        Path existingPath = pathUtil.getJobRootPath(userId, jobId);
        assertTrue(pathUtil.validatePath(existingPath), "存在的路径应返回true");
    }

    @Test
    @DisplayName("测试路径校验 - 路径不存在返回false")
    void testValidatePathNotExists() {
        Path nonExistingPath = tempDir.resolve("non_existing_path");

        assertFalse(pathUtil.validatePath(nonExistingPath), "不存在的路径应返回false");
    }

    @Test
    @DisplayName("测试路径校验 - null路径抛出异常")
    void testValidatePathNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            pathUtil.validatePath(null);
        });

        assertEquals("path不能为null", exception.getMessage());
    }

    @Test
    @DisplayName("测试获取原始输出目录路径")
    void testGetRawOutputPath() {
        Long userId = 1L;
        Long jobId = 100L;

        Path result = pathUtil.getRawOutputPath(userId, jobId);

        String pathString = result.toString();
        assertTrue(pathString.contains("raw_outputs"), "路径应包含 raw_outputs");
    }

    @Test
    @DisplayName("测试解析绝对路径 - 有效相对路径")
    void testResolveAbsolutePath() {
        Long userId = 1L;
        Long jobId = 100L;
        String relativePath = "inputs/test.txt";

        Path result = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);

        String pathString = result.toString();
        assertTrue(pathString.contains("user_1"), "路径应包含用户目录");
        assertTrue(pathString.contains("job_100"), "路径应包含任务目录");
        assertTrue(pathString.contains("inputs"), "路径应包含 inputs");
        assertTrue(pathString.contains("test.txt"), "路径应包含文件名");
    }

    @Test
    @DisplayName("测试解析绝对路径 - 路径穿越攻击防护")
    void testResolveAbsolutePathTraversal() {
        Long userId = 1L;
        Long jobId = 100L;
        String relativePath = "../../../etc/passwd";

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        });

        assertTrue(exception.getMessage().contains("相对路径不能超出任务根目录范围"),
                "应抛出安全异常");
    }

    @Test
    @DisplayName("测试获取相对路径")
    void testGetRelativePath() {
        Long userId = 1L;
        Long jobId = 100L;
        Path absolutePath = pathUtil.getInputPath(userId, jobId).resolve("test.txt");

        String result = pathUtil.getRelativePath(userId, jobId, absolutePath);

        assertTrue(result.contains("inputs"), "相对路径应包含 inputs");
        assertTrue(result.contains("test.txt"), "相对路径应包含文件名");
    }

    @Test
    @DisplayName("测试LAMMPS脚本文件名 - null参数抛出异常")
    void testGetLammpsScriptFilenameNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            pathUtil.getLammpsScriptFilename(null);
        });

        assertEquals("stage不能为null", exception.getMessage());
    }

    @Test
    @DisplayName("测试LAMMPS脚本文件名 - 空字符串抛出异常")
    void testGetLammpsScriptFilenameEmpty() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pathUtil.getLammpsScriptFilename("   ");
        });

        assertEquals("stage不能为空字符串", exception.getMessage());
    }

    @Test
    @DisplayName("测试轨迹文件名 - null参数抛出异常")
    void testGetTrajectoryFilenameNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            pathUtil.getTrajectoryFilename(null);
        });

        assertEquals("type不能为null", exception.getMessage());
    }

    @Test
    @DisplayName("测试轨迹文件名 - 空字符串抛出异常")
    void testGetTrajectoryFilenameEmpty() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pathUtil.getTrajectoryFilename("   ");
        });

        assertEquals("type不能为空字符串", exception.getMessage());
    }

    @Test
    @DisplayName("测试结果文件名 - null参数抛出异常")
    void testGetResultFilenameNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            pathUtil.getResultFilename(null);
        });

        assertEquals("property不能为null", exception.getMessage());
    }

    @Test
    @DisplayName("测试结果文件名 - 空字符串抛出异常")
    void testGetResultFilenameEmpty() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pathUtil.getResultFilename("   ");
        });

        assertEquals("property不能为空字符串", exception.getMessage());
    }

    @Test
    @DisplayName("测试临时文件名 - null jobId抛出异常")
    void testGetTempFilenameNullJobId() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            pathUtil.getTempFilename(null, "test");
        });

        assertEquals("jobId不能为null", exception.getMessage());
    }

    @Test
    @DisplayName("测试临时文件名 - null name抛出异常")
    void testGetTempFilenameNullName() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            pathUtil.getTempFilename(100L, null);
        });

        assertEquals("name不能为null", exception.getMessage());
    }

    @Test
    @DisplayName("测试临时文件名 - 负数jobId抛出异常")
    void testGetTempFilenameNegativeJobId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pathUtil.getTempFilename(-1L, "test");
        });

        assertEquals("jobId必须大于0", exception.getMessage());
    }

    @Test
    @DisplayName("测试临时文件名 - 空字符串name抛出异常")
    void testGetTempFilenameEmptyName() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pathUtil.getTempFilename(100L, "   ");
        });

        assertEquals("name不能为空字符串", exception.getMessage());
    }

    @Test
    @DisplayName("测试确保目录存在 - 创建新目录")
    void testEnsureDirectoryExistsCreate() {
        Path newDir = tempDir.resolve("new_directory");

        assertFalse(Files.exists(newDir), "目录不应存在");

        pathUtil.ensureDirectoryExists(newDir);

        assertTrue(Files.exists(newDir), "目录应被创建");
        assertTrue(Files.isDirectory(newDir), "应为目录");
    }

    @Test
    @DisplayName("测试确保目录存在 - 已存在目录不会出错")
    void testEnsureDirectoryExistsAlreadyExists() {
        Path existingDir = tempDir.resolve("existing_directory");
        pathUtil.ensureDirectoryExists(existingDir);

        pathUtil.ensureDirectoryExists(existingDir);

        assertTrue(Files.exists(existingDir), "目录应仍然存在");
    }

    @Test
    @DisplayName("测试确保目录存在 - null路径抛出异常")
    void testEnsureDirectoryExistsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            pathUtil.ensureDirectoryExists(null);
        });

        assertEquals("path不能为null", exception.getMessage());
    }

    @Test
    @DisplayName("测试不同用户的路径隔离")
    void testUserIsolation() {
        Long userId1 = 1L;
        Long userId2 = 2L;
        Long jobId = 100L;

        Path path1 = pathUtil.getJobRootPath(userId1, jobId);
        Path path2 = pathUtil.getJobRootPath(userId2, jobId);

        assertNotEquals(path1, path2, "不同用户的路径应该不同");
        assertTrue(path1.toString().contains("user_1"), "用户1的路径应包含 user_1");
        assertTrue(path2.toString().contains("user_2"), "用户2的路径应包含 user_2");
    }

    @Test
    @DisplayName("测试不同任务的路径隔离")
    void testJobIsolation() {
        Long userId = 1L;
        Long jobId1 = 100L;
        Long jobId2 = 200L;

        Path path1 = pathUtil.getJobRootPath(userId, jobId1);
        Path path2 = pathUtil.getJobRootPath(userId, jobId2);

        assertNotEquals(path1, path2, "不同任务的路径应该不同");
        assertTrue(path1.toString().contains("job_100"), "任务1的路径应包含 job_100");
        assertTrue(path2.toString().contains("job_200"), "任务2的路径应包含 job_200");
    }
}