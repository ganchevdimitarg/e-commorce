import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'Batch get products uses the real products/:batch-get path and returns the requested products'

    request {
        method POST()
        urlPath '/api/v1/catalog/products/:batch-get'
        headers {
            contentType(applicationJson())
        }
        body(
                items: ['33333333-3333-3333-3333-333333333333']
        )
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([
                [
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
                        comments: [],
                        version: 3,
                        avgStars: 4.5d,
                        ratingCount: 2
                ]
        ])
        bodyMatchers {
            jsonPath('$[0].price', byType())
            jsonPath('$[0].avgStars', byType())
        }
    }
}
