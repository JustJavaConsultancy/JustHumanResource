package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.request.enums.RequestType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface MonetaryBindingRepository extends JpaRepository<MonetaryBinding,Long> { Optional<MonetaryBinding> findByRequestTypeAndCurrency(RequestType type,String currency); List<MonetaryBinding> findAllByOrderById(); }
