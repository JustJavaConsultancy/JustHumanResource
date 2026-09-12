package com.justjava.humanresource.employeeexit.repository; import com.justjava.humanresource.employeeexit.entity.ExitPackageRule; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface ExitPackageRuleRepository extends JpaRepository<ExitPackageRule,Long>{List<ExitPackageRule> findByActiveTrue();}
