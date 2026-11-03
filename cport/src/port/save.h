#ifndef GTAVC_SAVE_H
#define GTAVC_SAVE_H

#include <stdbool.h>

struct World;
struct Game;

bool Save_exists(void);
void Save_write(struct World* w);
bool Save_load(struct World* w);
void Save_writeOptions(struct Game* g);
bool Save_loadOptions(struct Game* g);

#endif /* GTAVC_SAVE_H */
