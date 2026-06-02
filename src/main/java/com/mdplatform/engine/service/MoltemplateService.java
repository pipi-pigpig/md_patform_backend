package com.mdplatform.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.dto.FormulaRequest;
import com.mdplatform.engine.dto.PackmolResult;
import com.mdplatform.engine.model.MoleculeTemplate;
import com.mdplatform.engine.repository.MoleculeTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MoltemplateService {

    private final PathUtil pathUtil;
    private final AtomicFileService atomicFileService;
    private final PackmolService packmolService;
    private final MoleculeTemplateRepository moleculeTemplateRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Path serializeFormulaToJSON(Long userId, Long jobId, FormulaRequest request) throws IOException {
        log.info("序列化配方到JSON文件: userId={}, jobId={}", userId, jobId);

        pathUtil.createJobDirectories(userId, jobId);
        Path inputPath = pathUtil.getInputPath(userId, jobId);

        String filename = "formula_config.json";
        Path jsonFilePath = inputPath.resolve(filename);

        String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
        atomicFileService.writeAtomic(jsonFilePath, jsonContent);

        log.info("配方JSON文件已保存: {}", jsonFilePath);
        return jsonFilePath;
    }

    public Process executePythonModeling(Long userId, Long jobId) throws IOException {
        log.info("执行Python建模脚本: userId={}, jobId={}", userId, jobId);

        Path inputPath = pathUtil.getInputPath(userId, jobId);
        Path outputPath = pathUtil.getOutputPath(userId, jobId);

        Path formulaFile = inputPath.resolve("formula_config.json");
        if (!Files.exists(formulaFile)) {
            throw new IOException("配方配置文件不存在: " + formulaFile);
        }

        String pythonScriptPath = "python/moltemplate_modeling.py";

        ProcessBuilder processBuilder = new ProcessBuilder(
                "python",
                pythonScriptPath,
                "--user-id", userId.toString(),
                "--job-id", jobId.toString(),
                "--input", formulaFile.toString(),
                "--output", outputPath.toString()
        );

        processBuilder.redirectErrorStream(true);
        processBuilder.directory(new java.io.File("."));

        log.info("启动Python进程: {}", String.join(" ", processBuilder.command()));
        Process process = processBuilder.start();

        return process;
    }

    public Optional<MoleculeTemplate> getMoleculeTemplateByName(String name) {
        log.debug("查询分子模板: name={}", name);
        return moleculeTemplateRepository.findByMoleculeName(name);
    }

    public String readFormulaJSON(Long userId, Long jobId) throws IOException {
        log.info("读取配方JSON文件: userId={}, jobId={}", userId, jobId);

        Path inputPath = pathUtil.getInputPath(userId, jobId);
        Path formulaFile = inputPath.resolve("formula_config.json");

        if (!Files.exists(formulaFile)) {
            throw new IOException("配方配置文件不存在: " + formulaFile);
        }

        return Files.readString(formulaFile);
    }

    public boolean formulaFileExists(Long userId, Long jobId) {
        Path inputPath = pathUtil.getInputPath(userId, jobId);
        Path formulaFile = inputPath.resolve("formula_config.json");
        return Files.exists(formulaFile);
    }

    public ModelingWorkflowResult executeCompleteModelingWorkflow(Long userId, Long jobId, 
            FormulaRequest request) throws IOException {
        log.info("开始完整建模流程: userId={}, jobId={}", userId, jobId);

        Path formulaFile = serializeFormulaToJSON(userId, jobId, request);
        log.info("步骤1: 配方JSON文件已保存: {}", formulaFile);

        Process modelingProcess = executePythonModeling(userId, jobId);
        log.info("步骤2: Python建模脚本已启动");

        PackmolResult packmolResult = packmolService.executePackmol(userId, jobId, 
                formulaFile.toString());
        log.info("步骤3: Packmol堆积完成, 成功={}, 原子数={}", 
                packmolResult.getSuccess(), packmolResult.getAtomCount());

        ModelingWorkflowResult result = new ModelingWorkflowResult();
        result.setFormulaFilePath(pathUtil.getRelativePath(userId, jobId, formulaFile));
        result.setPackmolResult(packmolResult);
        result.setSuccess(packmolResult.getSuccess());

        if (packmolResult.getSuccess()) {
            log.info("建模流程完成: userId={}, jobId={}, PDB路径={}", 
                    userId, jobId, packmolResult.getPdbFilePath());
        } else {
            log.error("建模流程失败: userId={}, jobId={}, 错误={}", 
                    userId, jobId, packmolResult.getErrorMessage());
        }

        return result;
    }

    public PackmolResult executePackmolStep(Long userId, Long jobId) throws IOException {
        log.info("执行Packmol堆积步骤: userId={}, jobId={}", userId, jobId);

        Path inputPath = pathUtil.getInputPath(userId, jobId);
        Path formulaFile = inputPath.resolve("formula_config.json");

        if (!Files.exists(formulaFile)) {
            throw new IOException("配方配置文件不存在: " + formulaFile);
        }

        return packmolService.executePackmol(userId, jobId, formulaFile.toString());
    }

    public static class ModelingWorkflowResult {
        private boolean success;
        private String formulaFilePath;
        private PackmolResult packmolResult;
        private String errorMessage;

        public boolean getSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getFormulaFilePath() {
            return formulaFilePath;
        }

        public void setFormulaFilePath(String formulaFilePath) {
            this.formulaFilePath = formulaFilePath;
        }

        public PackmolResult getPackmolResult() {
            return packmolResult;
        }

        public void setPackmolResult(PackmolResult packmolResult) {
            this.packmolResult = packmolResult;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}