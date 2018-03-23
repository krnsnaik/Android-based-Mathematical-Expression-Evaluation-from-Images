#include <cstdlib>
#include <cstring>

#define SIZE 1024

Parser::Parser(Grammar *gram, FILE *fd, char *path) {
  g = gram;

  int n = strlen(path);

  if( n > 0 ) {
    pre = new char[n+1];
    strcpy(pre, path);
  }
  else {
    pre = new char[1];
    pre[0] = 0;
  }

  parse( fd );
}

Parser::~Parser() {
  delete[] pre;
}

bool Parser::isFillChar(char c) {
  switch(c) {
  case ' ':
  case '\t':
  case '\n':
  case '\r':
    return true;
  default: 
    return false;
  }
}

int Parser::split(char *str,char ***res){
  char tokensaux[2*SIZE];
  int n=0, i=0, j=0;

  while( isFillChar(str[i]) )  i++;

  while( str[i] ) {
    if( str[i] == '\"' ) {
      i++;
      while( str[i] && str[i] != '\"' ) {
	tokensaux[j] = str[i];
	i++; j++;
      }
      i++;
    }
    else {
      while( str[i] && !isFillChar(str[i]) ) {
	tokensaux[j] = str[i];
	i++; j++;
      }
    }
    tokensaux[j++] = 0;
    n++;
    while( str[i] && isFillChar(str[i]) )  i++;
  }

  char **toks=new char*[n];
  for(i=0, j=0; i<n; i++) {
    int tlen = strlen(&tokensaux[j])+1;
    toks[i] = new char[tlen];
    strcpy(toks[i], &tokensaux[j]);
    j += tlen;
  }

  *res = toks;

  return n;
}

