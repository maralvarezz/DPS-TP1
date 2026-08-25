# TP1 - Currency Converter

Desarrollo de Software Profesional (ITBA, 2026) - Trabajo Practico 1.

Este proyecto toma el `CurrencyConverter` revisado en la Clase 1 (paquete
`edu.itba.class1.exchange`: `CurrencyConverter`, `ExchangeRateProvider`,
`FreeCurrencyApiExchangeRateProvider`, `MoneyAmount`) y le agrega las 7
funcionalidades pedidas en la consigna, usando la API de
[freecurrencyapi.com](https://freecurrencyapi.com/).

## Diseno

La regla de negocio (`CurrencyConverter`) depende unicamente de la abstraccion
`ExchangeRateProvider`. No sabe si las cotizaciones vienen de una API HTTP, un
archivo o una base de datos. El unico detalle que sabe de HTTP/JSON es
`FreeCurrencyApiExchangeRateProvider` (usa Unirest + Gson, igual que en clase),
que implementa esa interfaz. Esto es el mismo ejemplo de inversion de
dependencias visto en la Clase 1 (`CurrencyConverter -> ExchangeRateProvider <-
FreeCurrencyApiExchangeRateProvider`), aplicado ahora a un contrato mas rico
(listar monedas, cotizacion simple, cotizacion en lote e historica) en lugar
del `getExchangeRate(from, to)` de una sola moneda que se vio en clase.

```
CurrencyConverter  --(depende de)-->  ExchangeRateProvider (interfaz)
                                              ^
                                              |
                                FreeCurrencyApiExchangeRateProvider (detalle: Unirest + Gson)
```

Los montos se modelan con `MoneyAmount` (wrapper inmutable de `BigDecimal`,
igual que en clase) y las monedas con `java.util.Currency` (no se reinventa un
tipo propio: la clase estandar ya da codigo, simbolo y nombre).

## Las 7 funcionalidades

| # | Historia de usuario | Donde vive |
|---|---|---|
| 1 | Listar monedas soportadas | `CurrencyConverter.listSupportedCurrencies()` |
| 2 | Timestamp de cuando se obtuvo la cotizacion | `ExchangeRate.fetchedAt()` / `ConversionResult.fetchedAt()` |
| 3 | Cotizacion entre dos monedas sin convertir un monto | `CurrencyConverter.getExchangeRate(from, to)` -> `ExchangeRate` |
| 4 | Manejo y notificacion clara de errores de conexion/API | Jerarquia `CurrencyExchangeException` (ver abajo) |
| 5 | Convertir un monto a varias monedas a la vez | `CurrencyConverter.convert(from, amount, List<Currency> to)` -> `ConversionResult` |
| 6 | Cotizacion historica para una fecha pasada | `CurrencyConverter.convert(from, amount, List<Currency> to, LocalDate date)` |
| 7 | Ver la cotizacion usada por cada moneda | `ConversionResult.conversions()` -> `Map<Currency, ConversionDetail>`, cada `ConversionDetail` trae `rate()` y `convertedAmount()` |

El metodo original `convert(Currency from, Currency to, MoneyAmount amount)`
visto en clase se mantiene (delega en el nuevo `convert(from, amount,
List.of(to))`), pero **ya no traga errores**: la version de clase hacia
`catch (Exception e) { System.err.println(...); } return MoneyAmount.ZERO;`,
que es exactamente el "simplemente fallar" que la consigna (historia 4) pide
evitar. Ahora la excepcion se propaga tal cual, tipada, para que el que llama
pueda reaccionar en vez de recibir un 0 silencioso.

## Manejo de errores (historia 4)

- `CurrencyExchangeConnectionException`: no se pudo hablar con la API (sin
  respuesta HTTP: timeout, DNS, conexion rechazada, etc).
- `CurrencyExchangeApiException`: la API respondio pero con error (401, 403,
  404, 429, 500...). Expone `getStatusCode()` y, cuando la API lo informa,
  `getErrorCode()` (p.ej. `invalid_api_key`).
- `UnknownCurrencyException`: se pidio una moneda cuya cotizacion no vino en
  la respuesta (p.ej. codigo mal escrito).

Las tres heredan de `CurrencyExchangeException` (unchecked), asi que
`CurrencyConverter` no necesita capturarlas: se propagan hasta quien use la
libreria, que decide como notificarlas (`Main` las captura una sola vez, en el
borde de la aplicacion, para imprimir un mensaje claro).

## Como correrlo

Requiere Java 21+ y una API key gratuita de freecurrencyapi.com (a diferencia
de la version de clase, la key **no** esta hardcodeada en el codigo).

```bash
mvn clean verify
export FREECURRENCYAPI_KEY=tu_api_key
mvn -q exec:java -Dexec.mainClass=edu.itba.class1.exchange.Main
# o: mvn -q compile && java -cp target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) edu.itba.class1.exchange.Main
```

`mvn clean verify` compila, corre todos los tests y genera el reporte de
cobertura JaCoCo en `target/site/jacoco/index.html`.

## Tests y cobertura

- `CurrencyConverterTest`: testea las reglas de negocio con un
  `ExchangeRateProvider` mockeado (Mockito) - sin red, sin HTTP.
- `FreeCurrencyApiExchangeRateProviderTest`: levanta un servidor HTTP local
  con WireMock y prueba la integracion real de Unirest + Gson contra el
  (incluyendo los 401/403/404/500, cuerpos no-JSON y respuestas vacias/
  malformadas), sin pegarle nunca a la API real ni necesitar una key valida.
- `MoneyAmountTest`: cubre `MoneyAmount` (igualdad, `multiply`, validaciones).

El `pom.xml` configura `jacoco-maven-plugin` para exigir 100% de cobertura de
lineas y de ramas (`mvn verify` falla si baja de eso), tal como pide la
consigna. La unica clase excluida de esa medicion es `Main`: es el composition
root/demo que llama a la API real y hace `System.out.println`, no logica de
negocio - excluirla es la practica habitual en cualquier setup de JaCoCo (no
tiene sentido "testear" un `main()` que imprime a consola y depende de una key
real).

**Nota:** este proyecto se armo en un entorno en la nube sin acceso a Maven
Central, asi que no se pudo correr `mvn clean verify` para confirmar en forma
automatica que compila y que la cobertura efectivamente da 100%. El codigo se
revisó a mano con mucho cuidado, pero corré `mvn clean verify` vos antes de
entregarlo, por las dudas.

## Requisitos no funcionales

1. Codigo limpio: metodos chicos y de una sola responsabilidad, nombres
   explicitos, sin duplicacion entre `convert(...)` y sus variantes (todas
   delegan en `buildConversionResult` / `rateFor`).
2. Dependencias: el negocio (`CurrencyConverter`) depende de una abstraccion
   (`ExchangeRateProvider`); el detalle (HTTP/JSON/Unirest/Gson) esta aislado
   en `FreeCurrencyApiExchangeRateProvider`.
3. Sin arquitectura especifica forzada, solo esa separacion negocio/detalle.
4. Cobertura de unit tests 100% (ver seccion anterior).
