package com.justjava.humanresource.employeeexit.repository; import com.justjava.humanresource.employeeexit.entity.ExitSettlementLine; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface ExitSettlementLineRepository extends JpaRepository<ExitSettlementLine,Long>{List<ExitSettlementLine> findBySettlementIdOrderById(Long id);}
