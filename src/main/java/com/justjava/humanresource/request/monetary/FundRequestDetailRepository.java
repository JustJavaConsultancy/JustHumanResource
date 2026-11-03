package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.request.enums.RequestType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface FundRequestDetailRepository extends JpaRepository<FundRequestDetail,Long> { Optional<FundRequestDetail> findByRequestId(Long id); }
