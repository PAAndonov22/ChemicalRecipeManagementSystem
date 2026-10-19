package org.codingburgas.ChemicalRecipeSystem.dto;

import java.time.LocalDate;

import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;

/** DTO: the safety data sheet shown next to a recipe. */
public class SafetyDataSheetDto {
    private final String hazardClass;
    private final SignalWord signalWord;
    private final String storageConditions;
    private final String protectiveEquipment;
    private final LocalDate revisionDate;

    public SafetyDataSheetDto(String hazardClass, SignalWord signalWord, String storageConditions,
                              String protectiveEquipment, LocalDate revisionDate) {
        this.hazardClass = hazardClass;
        this.signalWord = signalWord;
        this.storageConditions = storageConditions;
        this.protectiveEquipment = protectiveEquipment;
        this.revisionDate = revisionDate;
    }

    public String getHazardClass() { return hazardClass; }
    public SignalWord getSignalWord() { return signalWord; }
    public String getStorageConditions() { return storageConditions; }
    public String getProtectiveEquipment() { return protectiveEquipment; }
    public LocalDate getRevisionDate() { return revisionDate; }
}
