#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void) {
    const char *left = "abcdefghijklmnopqrstuvwxyz0123456789";
    const char *right = "ABCDEFGHIJKLMNOPQRSTUVWXYZ9876543210";
    const size_t left_len = strlen(left);
    const size_t right_len = strlen(right);
    const int64_t iterations = 1000000;
    int64_t checksum = 0;

    for (int64_t i = 0; i < iterations; i++) {
        const size_t size = left_len + right_len + 1;
        char *joined = malloc(size);
        if (joined == NULL) return 1;

        strcpy(joined, left);
        strcat(joined, right);
        checksum += (int64_t)strlen(joined);
        free(joined);
    }

    printf("%lld\n", (long long)checksum);
    return 0;
}
