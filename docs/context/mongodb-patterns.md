# MongoDB patterns (<service-name> only)

## Document with indexes
```java
@Document(collection = "products")
@CompoundIndex(def = "{'category': 1, 'price': -1}", name = "idx_category_price")
public class Product {
    @Id String id;
    @Indexed String sku;             // single-field index
    @TextIndexed String description; // text search
}
```

## Aggregation pipeline example
```java
Aggregation agg = Aggregation.newAggregation(
    Aggregation.match(Criteria.where("category").is(category).and("deletedAt").isNull()),
    Aggregation.group("brand").count().as("total").sum("price").as("totalValue"),
    Aggregation.sort(Sort.by(DESC, "total"))
);
return mongoTemplate.aggregate(agg, "products", BrandSummary.class).getMappedResults();
```

## Rules
- Equality filters first in compound index, range/sort last
- Never `findAll()` without filter on large collections
- Use `@DataMongoTest` for repository slice tests (faster than full `@SpringBootTest`)
- Mongock for schema migrations — never `mongock.enabled=false` in prod
