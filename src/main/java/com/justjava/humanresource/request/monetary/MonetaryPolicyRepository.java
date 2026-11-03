package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.request.enums.RequestType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface MonetaryPolicyRepository extends JpaRepository<MonetaryPolicy,Long> { List<MonetaryPolicy> findAllByOrderByCreatedAtDesc();
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from MonetaryPolicy p where p.id=:id") Optional<MonetaryPolicy> lockById(@Param("id") Long id); }
