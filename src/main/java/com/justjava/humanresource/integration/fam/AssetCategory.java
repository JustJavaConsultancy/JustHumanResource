package com.justjava.humanresource.integration.fam;


public enum AssetCategory {
    COMPUTERS_LAPTOPS("Computers & Laptops"),
    MONITORS_DISPLAYS("Monitors & Displays"),
    MOBILE_DEVICES("Mobile Devices (Phones/Tablets)"),
    NETWORKING_EQUIPMENT("Networking Equipment"),
    OFFICE_EQUIPMENT("Printers & Office Equipment"),
    OFFICE_FURNITURE("Office Furniture"),
    VEHICLES("Vehicles"),
    TOOLS_MACHINERY("Tools & Machinery"),
    SOFTWARE_LICENSES("Software Licenses"),
    SAFETY_EQUIPMENT("Safety Equipment / PPE"),
    OTHER("Other");

    private final String label;

    AssetCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}