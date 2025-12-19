package com.intelligent_data_analysis_system.utils.Generator;

public interface SqlGenerator {

    // ✅ 新签名：把 domain 透传进来
    SqlGenResult generate(String domain, String problem);
}
