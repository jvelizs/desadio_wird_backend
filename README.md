# Desafío Backend WIRD

Este proyecto consta con la solución al desafío planteado por wird. Para iniciar el proyecto es necesario primero configurar nuestras variables de entorno, dentro de un .env se definen las siguientes:
```
REDIS_HOST = Donde se verá alojado el servidor de redis
REDIS_PORT = El puerto donde se encuentra redis
API_KEY = La API key para poder hacer request a la API meteorológica
WEATHER_URL = https://api.tomorrow.io/v4/weather/realtime
```
Para el desarrollo se usó una imagen en docker de redis, en caso de querer hacer el mismo uso, primero es necesario instalar docker en el sistema en [Docker Platform](https://www.docker.com/products/docker-desktop/), luego hay que hacer el pull de redis en docker de la siguiente manera:

* Primero se hace el pull con `docker pull redis`
* Luego se corre la imagen en el puerto deseado `docker run --name redis -p 6379:6379 -d redis`

Con estos dos pasos, ya se encuentra disponible la imagen de redis para poder hacer uso del caché, solo que hay que especificar en las variables de entornos los parámetros colocados (Se usó localhost como host para redis).

Para la obtención de la apikey para acceder a los datos de la API meteorológica, se debe crear una cuenta en [Tomorrow.io](https://www.tomorrow.io/), ya teniendo la API key, se copia directamente dentro de las variables de entorno.

Ya teniendo configurada las variables de entorno, basta con hacer el build de la aplicación, existen dos métodos para realizar esto, uno es con el IDE de jetbrains [IntelliJ](https://www.jetbrains.com/es-es/), y si no por medio de consola:

 * En caso de tener IntelliJ, basta con abrir el proyecto y con la opción de RUN o ctrl + F5

 * En caso de no contar con IntelliJ, se puede por comandos, es necesario moverse a la carpeta del proyecto "Desafio_backend", desde ahí se abre una terminal y se coloca lo siguiente (Ojo, de esta forma es necesario poseer JDK):
    * De momento no hay test para probar el funcionamiento del proyecto, por lo que para hacer el build se coloca `./gradlew build -x test`
    * Luego de que se genera el build, se puede correr con `./gradlew run` o directamente con el java generado con `java -jar .\build\libs\desafio_backend-all.jar`

Con esto, ya está disponible para hacer uso. Para probar en recuperar la información vía API, entonces se debe tener en consideración lo siguiente:

1) El endpoint a consultar es: *localhost:8080/weather/{location}*
2) Location viene siendo el nombre de la localidad que se desea consultar, para efectos de solventar el desafío, la API solo recibe las siguientes locaciones:
    * santiago
    * zurich
    * auckland
    * sidney
    * london
    * georgia

    Es necesario colocar la locación de la misma forma que se muestra anteriormente de tal forma encuentra la información a buscar. De igual manera, en caso de querer agregar más localidades, es necesario agregar dentro del archivo `desafio_backend\src\main\resources\cities.yml`, guiándose por la forma:

    ```
        cities:
            santiago: santiago de chile
            zurich: zurich CH
            auckland: Auckland NZ
            sidney: Sidney Au
            london: London UK
            georgia: Georgia USA
            localidad_nueva: nombre de localidad más especifico (ej: Valparaiso Chile)
    ```