package com.rooti.domain.company.application;

import com.rooti.domain.company.domain.Company;
import com.rooti.domain.company.infrastructure.CompanyRepository;
import com.rooti.domain.company.presentation.dto.CreateRequest;
import com.rooti.domain.company.presentation.dto.Response;
import com.rooti.domain.company.presentation.dto.UpdateRequest;
import com.rooti.global.config.CacheConfig;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.CompanyNotFoundException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회사 도메인 use case.
 *
 * <p>RDS 비용 절감 관점에서 회사 정보는 거의 변하지 않는데 거의 모든 화면에서 참조되므로
 * Redis 캐시 적용 1순위입니다. {@code get(id)} 응답은 {@link CacheConfig#COMPANIES} 캐시에
 * 10분 TTL 로 저장되고, 변경 메서드({@code update / deactivate / create}) 가 실행되면
 * {@code @CacheEvict} 로 즉시 무효화됩니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;

    public Response create(CreateRequest req) {
        if (companyRepository.existsByName(req.name())) {
            throw new BusinessException(ErrorCode.CONFLICT, "동일한 이름의 회사가 이미 존재합니다.");
        }
        Company c =
                Company.builder()
                        .name(req.name())
                        .location(req.location())
                        .useFlag(true)
                        .imagePath(req.imagePath())
                        .templateId(req.templateId())
                        .templateData(req.templateData())
                        .build();
        return Response.from(companyRepository.save(c));
    }

    @CacheEvict(cacheNames = CacheConfig.COMPANIES, key = "#id")
    public Response update(long id, UpdateRequest req) {
        Company c = getOrThrow(id);
        c.rename(req.name());
        c.relocate(req.location());
        c.changeImage(req.imagePath());
        c.changeTemplate(req.templateId(), req.templateData());
        return Response.from(c);
    }

    @CacheEvict(cacheNames = CacheConfig.COMPANIES, key = "#id")
    public void deactivate(long id) {
        getOrThrow(id).deactivate();
    }

    /**
     * 회사 단건 조회 — 거의 모든 화면에서 호출되는 hot read 라 Redis 캐시 1순위.
     * 변경 발생 시 {@link #update}/{@link #deactivate} 가 같은 키로 evict.
     */
    @Cacheable(cacheNames = CacheConfig.COMPANIES, key = "#id")
    @Transactional(readOnly = true)
    public Response get(long id) {
        return Response.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<Response> search(String keyword, Pageable pageable) {
        return PageResponse.of(
                companyRepository.searchActive(keyword, pageable).map(Response::from));
    }

    public Company getOrThrow(long id) {
        return companyRepository.findById(id).orElseThrow(() -> new CompanyNotFoundException(id));
    }
}
