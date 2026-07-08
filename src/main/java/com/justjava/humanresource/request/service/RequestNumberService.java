package com.justjava.humanresource.request.service;
import com.justjava.humanresource.request.entity.RequestNumberCounter;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.repository.RequestNumberCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Year;
@Service @RequiredArgsConstructor public class RequestNumberService {
 private final RequestNumberCounterRepository repository;
 public String next(RequestType type){String prefix=switch(type){case STAFF_REQUISITION->"SRQ";case FILE_REQUEST->"FRQ";case ASSET_REQUEST->"ARQ";case EXPENSE_REIMBURSEMENT->"ERQ";case GENERAL_REQUEST->"REQ";}; int year=Year.now().getValue(); String key=prefix+"-"+year; RequestNumberCounter c=repository.findForUpdate(key).orElseGet(()->{RequestNumberCounter n=new RequestNumberCounter();n.setCounterKey(key);n.setNextValue(1);return repository.saveAndFlush(n);}); long value=c.getNextValue(); c.setNextValue(value+1); repository.save(c); return "%s-%d-%06d".formatted(prefix,year,value); }
}
