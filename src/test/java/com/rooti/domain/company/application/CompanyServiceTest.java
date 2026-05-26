package com.rooti.domain.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.rooti.domain.company.domain.Company;
import com.rooti.domain.company.infrastructure.CompanyRepository;
import com.rooti.domain.company.presentation.dto.CompanyDtos.CreateRequest;
import com.rooti.domain.company.presentation.dto.CompanyDtos.UpdateRequest;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock CompanyRepository companyRepository;
    @InjectMocks CompanyService companyService;

    @Test
    @DisplayName("Creating a company with a duplicated name surfaces CONFLICT, not 500")
    void create_duplicate_name() {
        when(companyRepository.existsByName("Acme")).thenReturn(true);

        assertThatThrownBy(
                        () -> companyService.create(new CreateRequest("Acme", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("Updating a missing company throws COMPANY_NOT_FOUND")
    void update_missing() {
        when(companyRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> companyService.update(9999L, new UpdateRequest(null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("Successful creation persists with useFlag = true")
    void create_persists() {
        when(companyRepository.existsByName(anyString())).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));

        var resp = companyService.create(new CreateRequest("Acme", "Seoul", null, null, null));

        assertThat(resp.name()).isEqualTo("Acme");
        assertThat(resp.useFlag()).isTrue();
    }
}
