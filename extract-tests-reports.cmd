docker create --name tmp-builder trino-my-driver:builder
docker cp tmp-builder:/app/target/surefire-reports ./
docker rm tmp-builder