/* Android JNI CAB extraction, independent of Wine. */
#include <jni.h>
#include <mspack.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <dirent.h>
#include <limits.h>
static const char *base_name(const char *s) {const char *b=s; for(;*s;s++) if(*s=='/'||*s=='\\')b=s+1; return b;}
static char *sibling(const char *src,const char *name) {
 if(!name||strcmp(name,base_name(name))||!strcmp(name,".")||!strcmp(name,".."))return NULL;
 char dir[PATH_MAX];if(strlen(src)>=sizeof(dir))return NULL;strcpy(dir,src);
 char *p=strrchr(dir,'/');if(!p)return NULL;*p=0;
 DIR *d=opendir(dir);if(!d)return NULL;struct dirent *e;char *result=NULL;
 while((e=readdir(d)))if(!strcasecmp(e->d_name,name)){size_t n=strlen(dir)+strlen(e->d_name)+2;result=malloc(n);if(result)snprintf(result,n,"%s/%s",dir,e->d_name);break;}
 closedir(d);return result;
}
JNIEXPORT jlong JNICALL Java_com_winlator_CabExtractor_extractFile(JNIEnv *env,jclass cls,jstring archive,jstring output,jstring wanted) {
 (void)cls;
 const char *src=(*env)->GetStringUTFChars(env,archive,NULL),*dst=(*env)->GetStringUTFChars(env,output,NULL),*name=(*env)->GetStringUTFChars(env,wanted,NULL);
 struct mscab_decompressor *dec=NULL;struct mscabd_cabinet *cab=NULL;char *paths[128]={0};int used=0;char error[256]="";jlong result=-1;
 if(!src||!dst||!name)goto done;
 dec=mspack_create_cab_decompressor(NULL);if(!dec){strcpy(error,"CAB decoder allocation failed");goto done;}
 cab=dec->open(dec,src);if(!cab){snprintf(error,sizeof(error),"CAB header error %d",dec->last_error(dec));goto done;}
 struct mscabd_cabinet *edge=cab;
 while(edge->prevname){
  if(used==128){strcpy(error,"Too many CAB volumes");goto done;}
  char *path=sibling(src,edge->prevname);if(!path){snprintf(error,sizeof(error),"Missing previous CAB: %.180s",edge->prevname);goto done;}paths[used++]=path;
  struct mscabd_cabinet *part=dec->open(dec,path);if(!part){strcpy(error,"Cannot open previous CAB");goto done;}
  if(dec->prepend(dec,edge,part)){dec->close(dec,part);strcpy(error,"Invalid previous CAB volume");goto done;}edge=part;
 }
 edge=cab;
 while(edge->nextname){
  if(used==128){strcpy(error,"Too many CAB volumes");goto done;}
  char *path=sibling(src,edge->nextname);if(!path){snprintf(error,sizeof(error),"Missing next CAB: %.180s",edge->nextname);goto done;}paths[used++]=path;
  struct mscabd_cabinet *part=dec->open(dec,path);if(!part){strcpy(error,"Cannot open next CAB");goto done;}
  if(dec->append(dec,edge,part)){dec->close(dec,part);strcpy(error,"Invalid next CAB volume");goto done;}edge=part;
 }
 for(struct mscabd_file *f=cab->files;f;f=f->next){if(strcasecmp(base_name(f->filename),name))continue;int rc=dec->extract(dec,f,dst);if(rc)snprintf(error,sizeof(error),"CAB decompression error %d for %.180s",rc,name);else result=f->length;break;}
 done:
 if(cab)dec->close(dec,cab);if(dec)mspack_destroy_cab_decompressor(dec);for(int i=0;i<used;i++)free(paths[i]);
 if(src)(*env)->ReleaseStringUTFChars(env,archive,src);if(dst)(*env)->ReleaseStringUTFChars(env,output,dst);if(name)(*env)->ReleaseStringUTFChars(env,wanted,name);
 if(*error){jclass e=(*env)->FindClass(env,"java/io/IOException");if(e)(*env)->ThrowNew(env,e,error);}return result;
}
