package com.intelligent_data_analysis_system.controller.healthcare;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.intelligent_data_analysis_system.domain.healthcare.mapper.PatientMasterIndexMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/healthcare")
@RequiredArgsConstructor
public class HealthcareSmokeController {

    private final PatientMasterIndexMapper patientMasterIndexMapper;

    @GetMapping("/patients/count")
    public Map<String, Object> countPatients() {
        long cnt = patientMasterIndexMapper.selectCount(new QueryWrapper<>());
        return Map.of("domain", "HEALTHCARE", "table", "patient_master_index", "count", cnt);
    }
}
