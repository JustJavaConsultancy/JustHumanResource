package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.RequestNumberCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface RequestNumberCounterRepository extends JpaRepository<RequestNumberCounter,String> { @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from RequestNumberCounter c where c.counterKey=:key") Optional<RequestNumberCounter> findForUpdate(@Param("key") String key); }
