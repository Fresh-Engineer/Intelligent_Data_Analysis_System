package com.intelligent_data_analysis_system.controller.finance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.intelligent_data_analysis_system.domain.finance.mapper.ClientsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceSmokeController {

    private final ClientsMapper clientsMapper;

    @GetMapping("/clients/count")
    public Map<String, Object> countClients() {
        long cnt = clientsMapper.selectCount(new QueryWrapper<>());
        return Map.of("domain", "FINANCE", "table", "clients", "count", cnt);
    }
}
