package com.rooti.domain.company.application;

import com.rooti.domain.company.domain.Company;
import com.rooti.domain.company.infrastructure.CompanyRepository;
import com.rooti.domain.company.presentation.dto.CreateRequest;
import com.rooti.domain.company.presentation.dto.Response;
import com.rooti.domain.company.presentation.dto.UpdateRequest;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Response update(long id, UpdateRequest req) {
        Company c = getOrThrow(id);
        c.rename(req.name());
        c.relocate(req.location());
        c.changeImage(req.imagePath());
        c.changeTemplate(req.templateId(), req.templateData());
        return Response.from(c);
    }

    public void deactivate(long id) {
        getOrThrow(id).deactivate();
    }

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
        return companyRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
    }
}
