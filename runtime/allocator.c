#include <stdlib.h>

/* Size of the header metadata pre-pended to each allocated memory block (16 bytes) */
#define HEADER_SIZE 16

/* Number of size classes for small allocation handling (power-of-two buckets from 8 to 16384+ bytes) */
#define N_CLASSES 14

/*
 * Header structure placed directly before the user's data pointer.
 * - 'size': Stores the size of the payload for tracking.
 * - 'next': Pointer to the next free block when this chunk is inside a free list.
 */
typedef struct Header {
    size_t size;
    struct Header *next;
} Header;

/*
 * Thread-local state: Each thread gets its own independent copy of these variables,
 * enabling lock-free dynamic memory allocation across multiple threads.
 */
static _Thread_local Header *free_lists[N_CLASSES];       /* Free lists organized by size class */
static _Thread_local unsigned char *arena_base;            /* Pointer to the start of the 1MB arena buffer */
static _Thread_local size_t arena_used;                    /* Number of bytes already carved out of the arena */
static _Thread_local size_t arena_cap = 1 << 20;           /* Arena capacity (1 MB = 1,048,576 bytes) */

/*
 * Calculates the size class index corresponding to a requested size.
 * Bucket sizes range from 8 up to (8 << (N_CLASSES - 2)), defaulting to the last bucket for larger requests.
 */
static size_t class_index(const size_t size) {
    size_t c = 8;
    for (size_t i = 0; i < N_CLASSES - 1; i++) {
        if (size <= c) return i;
        c <<= 1; /* Double the size threshold for the next class */
    }
    return N_CLASSES - 1;
}

/*
 * Returns the standard block payload size (in bytes) for a given class index.
 * Formula: 8 * (2 ^ idx)
 */
static size_t class_size(const size_t idx) {
    return (size_t)8 << idx;
}

/*
 * Allocates a block of memory of at least 'size' bytes.
 * Returns a pointer to the usable memory area (after the Header).
 */
void *zalloc(size_t size) {
    /* Handle zero-size allocation requests */
    if (size == 0) size = 1;

    /* Align size up to the nearest multiple of 16 for proper CPU memory access alignment */
    size = (size + 15) & ~((size_t)15);

    /*
     * Fallback for huge allocations (> 1 MB):
     * Skip the arena entirely and delegate directly to system malloc().
     */
    if (size > (1 << 20)) {
        Header *h = (Header *)malloc(size + HEADER_SIZE);
        if (!h) return NULL;
        h->size = size;
        h->next = NULL;
        return (void *)(h + 1); /* Return pointer immediately following the header */
    }

    /* Determine size class and target capacity */
    const size_t idx = class_index(size);
    size_t cs = class_size(idx);
    if (cs < size) cs = size;

    /*
     * Strategy 1: Free List Reuse
     * If a previously freed block of matching size class exists, recycle it.
     */
    if (free_lists[idx]) {
        Header *h = free_lists[idx];
        free_lists[idx] = h->next; /* Pop from top of free list */
        h->size = cs;
        return (void *)(h + 1);
    }

    /*
     * Strategy 2: Arena Allocation (Bump Pointer)
     * Lazy initialization: Allocate the thread-local 1MB arena on first use.
     */
    if (!arena_base) {
        arena_base = (unsigned char *)aligned_alloc(64, arena_cap);
    }

    /* Fallback if allocation failed or if the current arena does not have enough space left */
    if (!arena_base || arena_used + cs + HEADER_SIZE > arena_cap) {
        Header *h = (Header *)malloc(cs + HEADER_SIZE);
        if (!h) return NULL;
        h->size = cs;
        h->next = NULL;
        return (void *)(h + 1);
    }

    /* Bump-allocate a new chunk from the arena */
    Header *h = (Header *)(arena_base + arena_used);
    arena_used += cs + HEADER_SIZE;
    h->size = cs;
    h->next = NULL;
    return (void *)(h + 1);
}

/*
 * Frees a previously allocated memory block pointed to by 'p'.
 * Recycles small blocks into thread-local free lists for future re-allocation.
 */
void zfree(void *p) {
    if (p == NULL) return;

    /* Subtract HEADER_SIZE (1 Header structure back) to retrieve the metadata header */
    Header *h = (Header *)p - 1;

    /* Large allocations (> 1 MB) were allocated directly via system malloc(), so return them to system free() */
    if (h->size > (1 << 20)) {
        free(h);
        return;
    }

    /* Compute the size class and prepend the freed block to the head of the corresponding free list */
    const size_t idx = class_index(h->size);
    h->next = free_lists[idx];
    free_lists[idx] = h;
}