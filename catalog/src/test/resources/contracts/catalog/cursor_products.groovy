import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'Cursor pagination returns one page of products and the opaque next cursor'

    request {
        method GET()
        urlPath('/api/v1/catalog/products/:cursor') {
            queryParameters {
                parameter 'limit': '20'
            }
        }
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
                content: [
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
                ],
                nextCursor: 'next-cursor-token'
        )
        bodyMatchers {
            jsonPath('$.content[0].price', byType())
            jsonPath('$.content[0].avgStars', byType())
        }
    }
}
