package model;

/**
 * Classe POJO pour l'entité Categorie
 */
public class Categorie {
    private int id;
    private String nom;
    private String description;
    
    // Constructeurs
    public Categorie() {
    }
    
    public Categorie(String nom) {
        this.nom = nom;
    }
    
    public Categorie(int id, String nom, String description) {
        this.id = id;
        this.nom = nom;
        this.description = description;
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return nom;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Categorie categorie = (Categorie) obj;
        return id == categorie.id;
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}

