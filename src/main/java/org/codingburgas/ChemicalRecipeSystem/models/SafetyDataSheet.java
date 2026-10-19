package org.codingburgas.ChemicalRecipeSystem.models;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Safety data sheet (SDS) of a recipe: hazards, storage and protective equipment. One per recipe. */
@Entity
@Table(name = "safety_data_sheet")
public class SafetyDataSheet extends BaseEntity {
    /** e.g. "Flammable liquid, Cat. 2". */
    private String hazardClass;

    @Enumerated(EnumType.STRING)
    private SignalWord signalWord = SignalWord.NONE;

    @Column(length = 500)
    private String storageConditions;

    @Column(length = 500)
    private String protectiveEquipment;

    private LocalDate revisionDate = LocalDate.now();

    public SafetyDataSheet() {
    }

    public SafetyDataSheet(String hazardClass, SignalWord signalWord, String storageConditions, String protectiveEquipment) {
        this.hazardClass = hazardClass;
        this.signalWord = signalWord;
        this.storageConditions = storageConditions;
        this.protectiveEquipment = protectiveEquipment;
    }

    public String getHazardClass() { return hazardClass; }
    public void setHazardClass(String hazardClass) { this.hazardClass = hazardClass; }
    public SignalWord getSignalWord() { return signalWord; }
    public void setSignalWord(SignalWord signalWord) { this.signalWord = signalWord; }
    public String getStorageConditions() { return storageConditions; }
    public void setStorageConditions(String storageConditions) { this.storageConditions = storageConditions; }
    public String getProtectiveEquipment() { return protectiveEquipment; }
    public void setProtectiveEquipment(String protectiveEquipment) { this.protectiveEquipment = protectiveEquipment; }
    public LocalDate getRevisionDate() { return revisionDate; }
    public void setRevisionDate(LocalDate revisionDate) { this.revisionDate = revisionDate; }
}
