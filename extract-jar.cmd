docker create --name tmp trino-my-driver
docker cp tmp:/app/trino-my-driver.jar ./
docker rm tmp