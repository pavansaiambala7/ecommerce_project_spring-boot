<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="ie=edge">
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/css/bootstrap.min.css"
          integrity="sha384-Vkoo8x4CGsO3+Hhxv8T/Q5PaXtkKtu6ug5TOeNV6gBiFeWPGFN9MuhOf23Q9Ifjh" crossorigin="anonymous">
    <link rel="stylesheet" href="https://use.fontawesome.com/releases/v5.7.0/css/all.css"
          integrity="sha384-lZN37f5QGtY3VHgisS14W3ExzMWZxybE1SJSEsQp9S+oqd12jhcu+A56Ebc1zFSJ" crossorigin="anonymous">
    <title>Perishable Shop</title>
    <style>
        body { padding-bottom: 20px; }
        .card-body { min-height: 250px; }
        .card-img-top { max-height: 140px; object-fit: contain; }
    </style>
</head>
<body class="bg-light">

<nav class="navbar navbar-expand-lg navbar-light bg-light">
    <div class="container-fluid">
        <a class="navbar-brand" href="/">Perishable Shop</a>
        <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent"
                aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
        </button>

        <div class="collapse navbar-collapse" id="navbarSupportedContent">
            <span class="navbar-text mr-auto">Welcome <c:out value="${username}"/></span>
            <ul class="navbar-nav">
                <li class="nav-item"><a class="nav-link" href="/cart">Cart</a></li>
                <li class="nav-item"><a class="nav-link" href="/profileDisplay">Profile</a></li>
                <li class="nav-item">
                    <%-- Logout is POST-only so a third-party page cannot sign the user out. --%>
                    <form action="/logout" method="post" class="form-inline">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <button type="submit" class="btn btn-link nav-link">Logout</button>
                    </form>
                </li>
            </ul>
        </div>
    </div>
</nav>

<main>
    <div class="container mt-4">
        <h1>Welcome to Perishable Shop</h1>

        <c:if test="${not empty msg}">
            <div class="alert alert-info"><c:out value="${msg}"/></div>
        </c:if>

        <div class="row">
            <c:forEach var="product" items="${products}">
                <div class="col-md-3">
                    <div class="card mb-4">
                        <img class="card-img-top" src="<c:out value='${product.image}'/>"
                             alt="<c:out value='${product.name}'/>">
                        <div class="card-body">
                            <h4 class="card-title"><c:out value="${product.name}"/></h4>
                            <h6 class="card-text text-muted">
                                Category: <c:out value="${product.category.name}"/>
                            </h6>
                            <h5 class="card-text">
                                <fmt:formatNumber value="${product.price}" type="currency"/>
                            </h5>
                            <p class="card-text"><c:out value="${product.description}"/></p>

                            <c:choose>
                                <c:when test="${product.quantity gt 0}">
                                    <form action="/cart/add" method="post">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                        <input type="hidden" name="productId" value="${product.id}"/>
                                        <input type="hidden" name="quantity" value="1"/>
                                        <button type="submit" class="btn btn-primary">Add to Cart</button>
                                    </form>
                                </c:when>
                                <c:otherwise>
                                    <button class="btn btn-secondary" disabled>Out of stock</button>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                </div>
            </c:forEach>
        </div>
    </div>
</main>

<footer>
    <div class="container">
        <p class="text-muted">&copy; 2023 Perishable Shop. All rights reserved.</p>
    </div>
</footer>

<script src="https://code.jquery.com/jquery-3.4.1.slim.min.js"
        integrity="sha384-J6qa4849blE2+poT4WnyKhv5vZF5SrPo0iEjwBvKU7imGFAV0wwj1yYfoRSJoZ+n"
        crossorigin="anonymous"></script>
<script src="https://cdn.jsdelivr.net/npm/popper.js@1.16.0/dist/umd/popper.min.js"
        integrity="sha384-Q6E9RHvbIyZFJoft+2mJbHaEWldlvI9IOYy5n3zV9zzTtmI3UksdQRVvoxMfooAo"
        crossorigin="anonymous"></script>
<script src="https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/js/bootstrap.min.js"
        integrity="sha384-wfSDF2E50Y2D1uUdj0O3uMBJnjuUD4Ih7YwaYd1iqfktj0Uod8GCExl3Og8ifwB6"
        crossorigin="anonymous"></script>
</body>
</html>
