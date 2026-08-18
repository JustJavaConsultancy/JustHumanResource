package com.justjava.humanresource.request.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @Entity @Table(name="request_number_counters") public class RequestNumberCounter { @Id @Column(length=20) private String counterKey; @Column(nullable=false) private long nextValue; @Version private long version; }
