package com.example.medsim;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/internal/v1/knowledge")
class KnowledgeController {
    private final KnowledgeService knowledge;
    private final String token;

    KnowledgeController(KnowledgeService knowledge,@Value("${app.internal-service-token}") String token){this.knowledge=knowledge;this.token=token;}

    @PostMapping("/search")
    List<KnowledgeChunkView> search(@RequestHeader(value="X-Internal-Token",required=false) String supplied,
                                    @Valid @RequestBody KnowledgeSearchInput input){
        requireToken(supplied); return knowledge.search(input.symptomCodes(),input.query(),input.limit());
    }

    private void requireToken(String supplied){
        if(supplied==null||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,"INTERNAL_UNAUTHORIZED","内部服务鉴权失败");
    }
}
