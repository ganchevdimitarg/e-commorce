import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'Get product by id returns the product, its rating fields and a strong ETag'

    request {
        method GET()
        url '/api/v1/catalog/products/33333333-3333-3333-3333-333333333333'
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
            header('ETag', '"3"')
        }
        body(
                id: '33333333-3333-3333-3333-333333333333',
                name: 'mouse',
                description: 'WiFi mouse USB',
                price: 29.99d,
                inStock: true,
                characteristics: 'black',
                category: [
                        id: '22222222-2222-2222-2222-222222222222',
                        name: 'PC'
                ],
                version: 3,
                avgStars: 4.5d,
                ratingCount: 2
        )
        bodyMatchers {
            // Numeric JSON types are asserted by type so the contract pins neither the
            // serialised precision nor the Java type of the decimal fields.
            jsonPath('$.price', byType())
            jsonPath('$.avgStars', byType())
        }
    }
}
