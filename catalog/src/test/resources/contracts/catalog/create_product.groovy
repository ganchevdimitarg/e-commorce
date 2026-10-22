import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'Create product returns 201 with a Location header, a strong ETag and the created product'

    request {
        method POST()
        urlPath('/api/v1/catalog/products') {
            queryParameters {
                parameter 'categoryName': 'PC'
            }
        }
        headers {
            contentType(applicationJson())
        }
        body(
                name: 'mouse',
                description: 'WiFi mouse USB',
                price: 29.99d,
                inStock: true,
                characteristics: 'black'
        )
    }

    response {
        status CREATED()
        headers {
            contentType(applicationJson())
            header('Location', '/api/v1/catalog/products/33333333-3333-3333-3333-333333333333')
            header('ETag', '"0"')
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
                comments: [],
                version: 0,
                avgStars: 4.5d,
                ratingCount: 2
        )
        bodyMatchers {
            jsonPath('$.price', byType())
            jsonPath('$.avgStars', byType())
        }
    }
}
