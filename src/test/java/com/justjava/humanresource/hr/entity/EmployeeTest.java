package com.justjava.humanresource.hr.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EmployeeTest {

    @Test
    void getFullName_shouldReturnConcatenatedNames() {
        Employee employee = new Employee();
        employee.setFirstName("John");
        employee.setLastName("Doe");

        assertEquals("John Doe", employee.getFullName());
    }

    @Test
    void setEmergencyContact_shouldSetBidirectionalRelationship() {
        Employee employee = new Employee();
        EmergencyContact contact = new EmergencyContact();

        employee.setEmergencyContact(contact);

        assertSame(contact, employee.getEmergencyContact());
        assertSame(employee, contact.getEmployee());
    }

    @Test
    void setEmergencyContact_shouldAllowNullContact() {
        Employee employee = new Employee();
        employee.setEmergencyContact(null);

        assertNull(employee.getEmergencyContact());
    }
}
