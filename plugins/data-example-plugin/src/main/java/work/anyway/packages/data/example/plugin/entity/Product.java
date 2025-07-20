package work.anyway.packages.data.example.plugin.entity;

import work.anyway.interfaces.data.Entity;

/**
 * 产品实体示例
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class Product extends Entity {
  private String name;
  private String description;
  private double price;
  private int stock;

  // Getters and Setters
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public double getPrice() {
    return price;
  }

  public void setPrice(double price) {
    this.price = price;
  }

  public int getStock() {
    return stock;
  }

  public void setStock(int stock) {
    this.stock = stock;
  }
} 