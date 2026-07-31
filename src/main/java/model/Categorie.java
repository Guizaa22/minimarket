package model;

/**
 * Classe POJO pour l'entité Categorie
 */
public class Categorie {
    private int id;
    private String nom;
    private String description;
    /** Comportement métier ; ne dépend pas du libellé. */
    private TypeCategorie type = TypeCategorie.Standard;

    /** Photo illustrant la catégorie sur l'écran de caisse. Null si absente. */
    private byte[] image;
    private String imageMime;

    // Constructeurs
    public Categorie() {
    }

    public Categorie(String nom) {
        this.nom = nom;
    }

    public Categorie(String nom, TypeCategorie type) {
        this.nom = nom;
        this.type = type != null ? type : TypeCategorie.Standard;
    }

    public Categorie(int id, String nom, String description) {
        this.id = id;
        this.nom = nom;
        this.description = description;
    }

    public Categorie(int id, String nom, String description, TypeCategorie type) {
        this.id = id;
        this.nom = nom;
        this.description = description;
        this.type = type != null ? type : TypeCategorie.Standard;
    }

    public TypeCategorie getType() {
        return type != null ? type : TypeCategorie.Standard;
    }

    public void setType(TypeCategorie type) {
        this.type = type != null ? type : TypeCategorie.Standard;
    }

    /** true si la catégorie relève du tabac (paquet ou vente à l'unité). */
    public boolean estTabac() {
        return getType().estTabac();
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public String getImageMime() {
        return imageMime;
    }

    public void setImageMime(String imageMime) {
        this.imageMime = imageMime;
    }

    public boolean hasImage() {
        return image != null && image.length > 0;
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

