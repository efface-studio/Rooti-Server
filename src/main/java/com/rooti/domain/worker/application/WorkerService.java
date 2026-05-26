package com.rooti.domain.worker.application;

import com.rooti.domain.company.application.CompanyService;
import com.rooti.domain.company.domain.Company;
import com.rooti.domain.user.domain.User;
import com.rooti.domain.user.domain.UserRole;
import com.rooti.domain.user.infrastructure.UserRepository;
import com.rooti.domain.worker.domain.ChallengedWorker;
import com.rooti.domain.worker.domain.CompanyWorker;
import com.rooti.domain.worker.infrastructure.ChallengedWorkerRepository;
import com.rooti.domain.worker.infrastructure.CompanyWorkerRepository;
import com.rooti.domain.worker.presentation.dto.CompanyWorkerResponse;
import com.rooti.domain.worker.presentation.dto.CreateRequest;
import com.rooti.domain.worker.presentation.dto.Response;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker lifecycle: create the {@link User} + {@link ChallengedWorker} pair atomically,
 * search, hire/fire against a {@link Company}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkerService {

    private final UserRepository userRepository;
    private final ChallengedWorkerRepository workerRepository;
    private final CompanyWorkerRepository companyWorkerRepository;
    private final CompanyService companyService;
    private final PasswordEncoder passwordEncoder;

    public Response create(CreateRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BusinessException(ErrorCode.USER_USERNAME_DUPLICATED);
        }
        User user =
                User.builder()
                        .username(req.username())
                        .email(req.email())
                        .passwordHash(passwordEncoder.encode(req.password()))
                        .name(req.name())
                        .phoneNumber(req.phoneNumber())
                        .role(UserRole.WORKER)
                        .enabled(true)
                        .build();
        userRepository.save(user);
        ChallengedWorker worker = ChallengedWorker.of(user);
        workerRepository.save(worker);
        return Response.from(worker);
    }

    @Transactional(readOnly = true)
    public PageResponse<Response> search(String keyword, Pageable pageable) {
        return PageResponse.of(workerRepository.search(keyword, pageable).map(Response::from));
    }

    @Transactional(readOnly = true)
    public Response get(long id) {
        return Response.from(getOrThrow(id));
    }

    public ChallengedWorker getOrThrow(long id) {
        return workerRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKER_NOT_FOUND));
    }

    // ----- Hiring relationship -------------------------------------------------
    public CompanyWorkerResponse hire(long companyId, long workerId) {
        if (companyWorkerRepository.existsByCompanyIdAndWorkerId(companyId, workerId)) {
            throw new BusinessException(ErrorCode.WORKER_ALREADY_HIRED);
        }
        Company company = companyService.getOrThrow(companyId);
        ChallengedWorker worker = getOrThrow(workerId);
        CompanyWorker cw = companyWorkerRepository.save(CompanyWorker.hire(company, worker));
        return CompanyWorkerResponse.from(cw);
    }

    public void fire(long companyWorkerId) {
        CompanyWorker cw =
                companyWorkerRepository
                        .findById(companyWorkerId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.WORKER_NOT_FOUND));
        cw.fire();
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyWorkerResponse> listByCompany(long companyId, Pageable pageable) {
        return PageResponse.of(
                companyWorkerRepository
                        .findAllByCompany(companyId, pageable)
                        .map(CompanyWorkerResponse::from));
    }
}
