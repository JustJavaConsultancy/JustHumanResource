package com.justjava.humanresource.request.monetary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MonetaryPolicyEventRepository extends JpaRepository<MonetaryPolicyEvent,Long> {
 List<MonetaryPolicyEvent> findTop100ByOrderByIdDesc();
}
