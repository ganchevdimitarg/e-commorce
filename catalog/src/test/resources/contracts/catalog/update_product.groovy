import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'Update product honours If-Match and returns the updated product with the next strong ETag'

    request {
        method PUT()
        url '/api/v1/catalog/products/33333333-3333-3333-3333-333333333333'
        headers {
            contentType(applicationJson())
            header('If-Match', '"3"')
            // Consumers generate a fresh key per request; only the producer side is concrete.
            header('Idempotency-Key', $(consumer(regex('.+')), producer('11111111-1111-1111-1111-111111111111')))
        }
        body(
                description: 'WiFi mouse USB v2',
                price: 19.99d,
                inStock: false,
                characteristics: 'black'
        )
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
            header('ETag', '"4"')
        }
        body(
                id: '33333333-3333-3333-3333-333333333333',
                name: 'mouse',
                description: 'WiFi mouse USB v2',
                price: 19.99d,
                inStock: false,
                characteristics: 'black',
                category: [
                        id: '22222222-2222-2222-2222-222222222222',
                        name: 'PC'
                ],
                version: 4,
                avgStars: 4.5d,
                ratingCount: 2
        )
        bodyMatchers {
            jsonPath('$.price', byType())
            jsonPath('$.avgStars', byType())
        }
    }
}
