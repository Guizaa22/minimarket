package model;

import java.time.LocalDateTime;

/**
 * Classe POJO pour l'entité Fournisseur (Supplier)
 */
public class Fournisseur {
    private int id;
    private String nom;
    private String telephone;
    private String email;
    private String adresse;
    private LocalDateTime dateCreation;
    
    // Constructeurs
    public Fournisseur() {
    }
    
    public Fournisseur(String nom, String telephone, String email, String adresse) {
        this.nom = nom;
        this.telephone = telephone;
        this.email = email;
        this.adresse = adresse;
    }
    
    public Fournisseur(int id, String nom, String telephone, String email, String adresse, LocalDateTime dateCreation) {
        this.id = id;
        this.nom = nom;
        this.telephone = telephone;
        this.email = email;
        this.adresse = adresse;
        this.dateCreation = dateCreation;
    }
    
    // Getters et Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getNom() {
        return nom;
    }
    
    public void setNom(String nom) {
        this.nom = nom;
    }
    
    public String getTelephone() {
        return telephone;
    }
    
    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getAdresse() {
        return adresse;
    }
    
    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }
    
    public LocalDateTime getDateCreation() {
        return dateCreation;
    }
    
    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }
    
    @Override
    public String toString() {
        return nom;
    }
}

